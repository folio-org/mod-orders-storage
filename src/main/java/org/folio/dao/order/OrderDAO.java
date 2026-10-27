package org.folio.dao.order;

import java.util.Collection;
import java.util.Map;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;

public interface OrderDAO {
  Future<PurchaseOrder> getOrderById(String orderId, Conn conn);
  Future<Map<String, PurchaseOrder>> getOrdersByIds(Collection<String> orderIds, String tenantId, Conn conn);
  Future<PurchaseOrder> getOrderByIdForUpdate(String orderId, Conn conn);
  Future<Void> updateOrder(PurchaseOrder po, Conn conn);
  Future<Long> getNextPoNumber(Conn conn);
}
