package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.util.List;

import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

public class PurchaseOrdersAPITest extends TestBase {
  private static final String ORDERS_ENDPOINT = "/orders-storage/orders";
  private final PurchaseOrder purchaseOrderSample = getOrder("data/purchase-orders/81_ongoing_pending.json");

  @Test
  public void shouldSuccessfullyDeletePurchaseOrder() throws MalformedURLException {
      String purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), JsonObject.mapFrom(purchaseOrderSample).encode());
      List<PurchaseOrder> beforeDeleteOrders = getViewCollection(ORDERS_ENDPOINT);
      assertThat(beforeDeleteOrders, hasSize(1));

      deleteData(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId);

      List<PurchaseOrder> afterOrders = getViewCollection(ORDERS_ENDPOINT);
      assertThat(afterOrders, hasSize(0));
  }


  @Test
  public void shouldFailedWhenTryDeletePurchaseOrder() throws MalformedURLException {
    String purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), JsonObject.mapFrom(purchaseOrderSample).encode());
    List<PurchaseOrder> beforeDeleteOrders = getViewCollection(ORDERS_ENDPOINT);
    assertThat(beforeDeleteOrders, hasSize(1));

    deleteData(PURCHASE_ORDER.getEndpointWithId(), "non-existed");

    List<PurchaseOrder> afterOrders = getViewCollection(ORDERS_ENDPOINT);
    assertThat(afterOrders, hasSize(1));
  }

  private PurchaseOrder getOrder(String path) {
    return getFileAsObject(path, PurchaseOrder.class);
  }


  private List<PurchaseOrder> getViewCollection(String endpoint) throws MalformedURLException {
    return getData(endpoint).as(PurchaseOrderCollection.class)
      .getPurchaseOrders();
  }
}
