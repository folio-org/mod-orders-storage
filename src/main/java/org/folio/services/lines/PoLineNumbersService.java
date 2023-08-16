package org.folio.services.lines;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.order.OrderDAO;
import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;

public class PoLineNumbersService {
  private static final Logger log = LogManager.getLogger();

  private final OrderDAO orderDAO;
  private final PoLinesService poLinesService;

  public PoLineNumbersService(OrderDAO orderDAO, PoLinesService poLinesService) {
    this.orderDAO = orderDAO;
    this.poLinesService = poLinesService;
  }

  /**
   * Returns poLineNumbers new po line numbers for the order.
   */
  public Future<PoLineNumber> retrievePoLineNumber(String purchaseOrderId, int poLineNumbers, DBClient dbClient) {
    PostgresClient pgClient = dbClient.getPgClient();
    log.debug("retrievePoLineNumber: getting po {} for update", purchaseOrderId);
    return pgClient.withTrans(conn -> orderDAO.getOrderByIdForUpdate(purchaseOrderId, conn)
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
        return orderDAO.updateOrder(po, conn)
          .map(v -> nextNumber);
      })
      .map(n -> {
        List<String> sequenceNumbers = IntStream.range(n, n+poLineNumbers)
          .mapToObj(Integer::toString)
          .toList();
        log.debug("retrievePoLineNumber: done, po {}", purchaseOrderId);
        return new PoLineNumber().withSequenceNumbers(sequenceNumbers);
      })
      .onFailure(t -> log.error("retrievePoLineNumber: failed to retrieve number for po {}", purchaseOrderId, t))
    );
  }
}
