package org.folio.services.lines;

import io.vertx.core.Future;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.order.OrderSequenceRequestBuilder;

import java.util.ArrayList;
import java.util.List;

public class PoLineNumbersService {
  private OrderSequenceRequestBuilder orderSequenceBuilder;
  private PostgresClientFactory pgClientFactory;

  public PoLineNumbersService(OrderSequenceRequestBuilder orderSequenceBuilder, PostgresClientFactory pgClientFactory) {
    this.orderSequenceBuilder = orderSequenceBuilder;
    this.pgClientFactory = pgClientFactory;
  }

  /**
   * Retrieves next po line number from sequence for order.
   *
   * @param purchaseOrderId the order id
   * @param poLineNumbers the po line numbers
   * @param tenantId the tenant id
   * @return future with po line number
   */
  public Future<PoLineNumber> retrievePoLineNumber(String purchaseOrderId, int poLineNumbers, String tenantId) {
    PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
    return pgClient.select(orderSequenceBuilder.buildPOLNumberQuery(purchaseOrderId, poLineNumbers))
      .map(results -> {
        List<String> sequenceNumbers = new ArrayList<>();
        results.forEach(row -> sequenceNumbers.add(row.getLong(0).toString()));
        return new PoLineNumber().withSequenceNumbers(sequenceNumbers);
      });
  }
}
