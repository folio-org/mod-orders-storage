package org.folio.services.lines;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;

import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;

public class PoLineNumbersService {
  private static final Logger log = LogManager.getLogger();

  private final PoLinesService poLinesService;

  public PoLineNumbersService(PoLinesService poLinesService) {
    this.poLinesService = poLinesService;
  }

  /**
   * Retrieves next po line number from sequence for order.
   */
  public Future<PoLineNumber> retrievePoLineNumber(String purchaseOrderId, int poLineNumbers, DBClient dbClient) {
    PostgresClient pgClient = dbClient.getPgClient();
    log.debug("retrievePoLineNumber: getting po {} for update", purchaseOrderId);
    return pgClient.withConn(conn -> conn.getByIdForUpdate(PURCHASE_ORDER_TABLE, purchaseOrderId, PurchaseOrder.class)
      .compose(po -> {
        if (po.getNextPolNumber() != null)
          return Future.succeededFuture(po);
        log.warn("nextPolNumber missing for po {}, calculating from lines", purchaseOrderId);
        return poLinesService.getLastLineNumber(purchaseOrderId, conn)
          .map(lastUsedNumber -> po.withNextPolNumber(lastUsedNumber + 1));
      })
      .compose(po -> {
        log.debug("Updating po {} with new nextPolNumber", purchaseOrderId);
        int nextNumber = po.getNextPolNumber();
        po.setNextPolNumber(nextNumber + poLineNumbers);
        return conn.update(PURCHASE_ORDER_TABLE, po, purchaseOrderId)
          .map(v -> nextNumber);
      })
      .map(n -> {
        List<String> sequenceNumbers = IntStream.range(n, n+poLineNumbers).mapToObj(Integer::toString).collect(toList());
        log.debug("retrievePoLineNumber: done, po {}", purchaseOrderId);
        return new PoLineNumber().withSequenceNumbers(sequenceNumbers);
      })
      .onFailure(t -> log.error("retrievePoLineNumber: failed to retrieve number for po {}", purchaseOrderId, t))
    );
  }
}
