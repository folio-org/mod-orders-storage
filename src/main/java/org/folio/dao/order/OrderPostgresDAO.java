package org.folio.dao.order;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;

import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;

public class OrderPostgresDAO implements OrderDAO {
  private static final Logger log = LogManager.getLogger();

  @Override
  public Future<PurchaseOrder> getOrderByIdForUpdate(String orderId, Conn conn) {
    return conn.getByIdForUpdate(PURCHASE_ORDER_TABLE, orderId, PurchaseOrder.class)
      .onFailure(t -> log.error("getOrderByIdForUpdate failed for order with id {}", orderId, t));
  }

  @Override
  public Future<Void> updateOrder(PurchaseOrder po, Conn conn) {
    return conn.update(PURCHASE_ORDER_TABLE, po, po.getId())
      .onFailure(t -> log.error("updateOrder failed for order with id {}", po.getId(), t))
      .mapEmpty();
  }
}
