package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

@IsolatedTenant
public class PurchaseOrdersAPITest extends TestBase {
  private static final String ORDERS_ENDPOINT = "/orders-storage/orders";
  private final PurchaseOrder purchaseOrderSample = getOrder("data/purchase-orders/81_ongoing_pending.json");

  @Test
  public void shouldSuccessfullyDeletePurchaseOrder() throws MalformedURLException {
      String purchaseOrderSampleId = UUID.randomUUID().toString();
      JsonObject purchaseOrder = JsonObject.mapFrom(purchaseOrderSample);
      purchaseOrder.put("id", purchaseOrderSampleId);
      purchaseOrderSampleId = createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrder.encode(), ISOLATED_TENANT_HEADER);
      List<PurchaseOrder> beforeDeleteOrders = getViewCollection(ORDERS_ENDPOINT);
      assertThat(beforeDeleteOrders, hasSize(1));

      deleteData(PURCHASE_ORDER.getEndpointWithId(), purchaseOrderSampleId, ISOLATED_TENANT_HEADER);

      List<PurchaseOrder> afterOrders = getViewCollection(ORDERS_ENDPOINT);
      assertThat(afterOrders, hasSize(0));
  }


  @Test
  public void shouldFailedWhenTryDeletePurchaseOrder() throws MalformedURLException {
    String purchaseOrderSampleId = UUID.randomUUID().toString();
    JsonObject purchaseOrder = JsonObject.mapFrom(purchaseOrderSample);
    purchaseOrder.put("id", purchaseOrderSampleId);
    createEntity(PURCHASE_ORDER.getEndpoint(), purchaseOrder.encode(), ISOLATED_TENANT_HEADER);
    List<PurchaseOrder> beforeDeleteOrders = getViewCollection(ORDERS_ENDPOINT);
    assertThat(beforeDeleteOrders, hasSize(1));

    String nonExistedId = UUID.randomUUID().toString();
    deleteData(PURCHASE_ORDER.getEndpointWithId(), nonExistedId, ISOLATED_TENANT_HEADER);

    List<PurchaseOrder> afterOrders = getViewCollection(ORDERS_ENDPOINT);
    assertThat(afterOrders, hasSize(1));
  }

  private PurchaseOrder getOrder(String path) {
    return getFileAsObject(path, PurchaseOrder.class);
  }


  private List<PurchaseOrder> getViewCollection(String endpoint) throws MalformedURLException {
    return getData(endpoint, ISOLATED_TENANT_HEADER).as(PurchaseOrderCollection.class)
      .getPurchaseOrders();
  }
}
