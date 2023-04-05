package org.folio.dao.order;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;

public interface OrderDAO {
  Future<PurchaseOrder> getOrderByIdForUpdate(String orderId, Conn conn);
  Future<Void> updateOrder(PurchaseOrder po, Conn conn);
}
