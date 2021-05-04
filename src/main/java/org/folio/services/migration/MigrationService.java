package org.folio.services.migration;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.persist.ResponseUtils.handleFailure;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.models.Holding;
import org.folio.rest.acq.model.finance.Fund;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.jaxrs.model.Eresource;
import org.folio.rest.jaxrs.model.Eresource.CreateInventory;
import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Physical;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.Piece.Format;
import org.folio.rest.jaxrs.model.Piece.ReceivingStatus;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.finance.FinanceService;
import org.folio.services.inventory.InventoryService;
import org.folio.services.lines.PoLinesService;
import org.folio.services.piece.PieceService;

public class MigrationService {

  private static final Logger log = LogManager.getLogger(MigrationService.class);
  public static final int MAX_RQ_ITEMS = 15;
  private final FinanceService financeService;
  private final PoLinesService poLinesService;
  private final InventoryService inventoryService;
  private final PieceService pieceService;

  public MigrationService(FinanceService financeService, PoLinesService poLinesService, InventoryService inventoryService,
    PieceService pieceService) {
    this.financeService = financeService;
    this.poLinesService = poLinesService;
    this.inventoryService = inventoryService;
    this.pieceService = pieceService;
  }

  public Future<Void> syncAllFundCodeFromPoLineFundDistribution(Map<String, String> headers, Context vertxContext) {
    Promise<Void> promise = Promise.promise();
    vertxContext.runOnContext(v -> {
      log.debug("Cross Migration for fund code synchronization started");
      DBClient client = new DBClient(vertxContext, headers);
      financeService.getAllFunds(new RequestContext(vertxContext, headers))
        .thenAccept(funds -> runSetFundCodeIntoPolScript(funds, client)
          .onSuccess(v1 -> {
            log.debug("Cross Migration for fund code synchronization completed");
            promise.complete();
          })
          .onFailure(v2 -> {
            log.error("Cross Migration for fund code synchronization failed");
            promise.fail(v2.getCause());
          })
        )
        .exceptionally(throwable -> {
          log.error("Cross Migration for fund code synchronization failed");
          promise.fail(throwable.getCause());
          return null;
        });

    });
    return promise.future();
  }


  public Future<Void> runSetFundCodeIntoPolScript(List<Fund> funds, DBClient client) {
    Promise<Void> promise = Promise.promise();
    String schemaName = PostgresClient.convertToPsqlStandard(client.getTenantId());
    String sql = "DO\n" + "$$\n" + "begin\n" + " PERFORM %s.set_fund_code_into_pol('%s');\n" + "end;\n"
      + "$$ LANGUAGE plpgsql;";
    try {
      String json = ObjectMapperTool.getMapper().writeValueAsString(funds);
      client.getPgClient()
        .execute(String.format(sql, schemaName, json), event -> {
          if (event.succeeded()) {
            promise.complete();
          } else {
            handleFailure(promise, event);
          }
        });
    } catch (JsonProcessingException e) {
      promise.fail(e.getCause());
    }
    return promise.future();
  }

  public static String getFullTableName(String tenantId, String tableName) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + tableName;
  }

  public Future<Void> syncHoldingIds(Map<String, String> headers, Context vertxContext) {
    return poLinesService.getOpenOrderPoLines(new DBClient(vertxContext, headers))
      .map(this::getHoldingsPoLines)
      .compose(poLines -> rebuildPoLineLocations(poLines, vertxContext, headers)
        .compose(v -> fetchHoldingsForPoLines(poLines, vertxContext, headers))
        .compose(v -> persistPoLines(poLines, vertxContext, headers)));
  }

  public List<PoLine> getHoldingsPoLines(List<PoLine> poLines) {
    return poLines.stream()
      .filter(poLine -> {
          return Optional.ofNullable(poLine)
            .map(PoLine::getEresource)
            .map(Eresource::getCreateInventory)
            .equals(Optional.of(CreateInventory.INSTANCE_HOLDING)) ||
            Optional.ofNullable(poLine)
              .map(PoLine::getEresource)
              .map(Eresource::getCreateInventory)
              .equals(Optional.of(CreateInventory.INSTANCE_HOLDING_ITEM)) ||
            Optional.ofNullable(poLine)
              .map(PoLine::getPhysical)
              .map(Physical::getCreateInventory)
              .equals(Optional.of(Physical.CreateInventory.INSTANCE_HOLDING)) ||
            Optional.ofNullable(poLine)
              .map(PoLine::getPhysical)
              .map(Physical::getCreateInventory)
              .equals(Optional.of(Physical.CreateInventory.INSTANCE_HOLDING_ITEM));
        }
      ).collect(toList());
  }

  private Future<Void> persistPoLines(List<PoLine> poLines, Context vertxContext, Map<String, String> headers) {
    Promise<Void> promise = Promise.promise();

    CompletableFuture[] futures = poLines.stream()
      .map(poLine -> poLinesService.updatePoLine(poLine, new DBClient(vertxContext, headers)).toCompletionStage()).toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures)
      .thenAccept(v -> promise.complete())
      .exceptionally(throwable -> {
        promise.fail(throwable.getCause());
        return null;
      });

    return promise.future();
  }

  private Future<Void> fetchHoldingsForPoLines(List<PoLine> poLines, Context vertxContext, Map<String, String> headers) {
    Promise<Void> promise = Promise.promise();

    List<Pair<Location, PoLine>> locationPolinePairs = poLines.stream()
      .flatMap(poLine -> poLine.getLocations().stream().map(location -> Pair.of(location, poLine)))
      .collect(toList());

    List<Pair<Location, PoLine>> uniqueLocationPolinePairs = StreamEx.of(locationPolinePairs)
      .distinct(it -> it.getLeft().getLocationId() + it.getRight().getInstanceId()).toList();

    CompletableFuture<List<Holding>>[] futures = StreamEx.ofSubLists(uniqueLocationPolinePairs, MAX_RQ_ITEMS).map(items -> {
      return inventoryService
        .getHoldingByInstanceIdAndLocation(new RequestContext(vertxContext, headers), items)
        .exceptionally(throwable -> {
          log.error(throwable.getCause());
          promise.fail(throwable.getCause());
          return null;
        });

    }).toArray(CompletableFuture.class);

    CompletableFuture<List<List<Holding>>> listCompletableFuture = CompletableFuture.allOf(futures)
      .thenApply(v -> List.of(futures).stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .collect(toList())
      );

    listCompletableFuture.thenAccept(ids -> {
      List<Holding> holdings = ids.stream().flatMap(Collection::stream).collect(toList());

      poLines.forEach(poLine -> poLine.getLocations().forEach(location -> {
        Optional<Holding> poLineHolding = holdings.stream().filter(
          holding -> holding.getInstanceId().equals(poLine.getInstanceId()) && holding.getPermanentLocationId()
            .equals(location.getLocationId())).findFirst();

        poLineHolding.ifPresent(holding -> {
          location.setHoldingId(holding.getId());
          location.setLocationId(null);
        });
      }));

      log.info(ids);
      promise.complete();
    })
      .exceptionally(throwable -> {
        promise.fail(throwable.getCause());
        return null;
      });
    return promise.future();
  }

  private Future<Void> rebuildPoLineLocations(List<PoLine> poLines, Context vertxContext, Map<String, String> headers) {
    Promise<Void> promise = Promise.promise();

    CompletableFuture<List<Piece>>[] futures = poLines.stream()
      .map(poLine -> pieceService.getPiecesForPoLine(poLine.getId(), new DBClient(vertxContext, headers)).toCompletionStage())
      .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures)
      .thenAccept(v -> {
        List.of(futures).stream()
          .map(CompletableFuture::join)
          .forEach(pieces -> {
            pieces.forEach(piece -> {
              boolean atLeastOneReceivedPiece = pieces.stream()
                .anyMatch(p -> p.getReceivingStatus() == ReceivingStatus.RECEIVED);

              Optional<PoLine> relatedPoLine = poLines.stream()
                .filter(p -> p.getId().equals(piece.getPoLineId())).findFirst();
              boolean manualAddPiecesForReceiving = relatedPoLine.map(PoLine::getCheckinItems).equals(Optional.of(Boolean.FALSE));

              if (atLeastOneReceivedPiece && manualAddPiecesForReceiving) {
                // rebuild poLine Location
                Map<String, Map<Format, Long>> groupedQuantityByLocation = pieces.stream().collect(
                  Collectors.groupingBy(Piece::getLocationId, Collectors.groupingBy(Piece::getFormat, Collectors.counting())));

                List<Location> locations = groupedQuantityByLocation.entrySet().stream().map(group -> {
                  Location location = new Location();
                  String locationId = group.getKey();

                  location.withLocationId(group.getKey())
                    .withLocationId(locationId);

                  int quantityElectronic = 0;
                  int quantityPhysical = 0;

                  if (group.getValue().containsKey(Format.ELECTRONIC)) {
                    quantityElectronic = group.getValue().get(Format.ELECTRONIC).intValue();
                    location.setQuantityElectronic(quantityElectronic);
                  }

                  if (group.getValue().containsKey(Format.PHYSICAL)) {
                    quantityPhysical = group.getValue().get(Format.PHYSICAL).intValue();
                    location.setQuantityPhysical(quantityPhysical);
                  }
                  location.setQuantity(quantityElectronic + quantityPhysical);
                  return location;
                }).collect(toList());

                relatedPoLine.ifPresent(p -> p.setLocations(locations));
              }
            });
          });

        promise.complete();
      })
    .exceptionally(throwable -> {
      promise.fail(throwable.getCause());
      return null;
    });
    return promise.future();
  }
}
