package org.folio.dao.order;

import io.vertx.core.Future;
import org.folio.rest.exceptions.HttpException;
import io.vertx.sqlclient.Row;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;

import static org.folio.models.TableNames.ORDER_NUMBER_TABLE;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;

public class OrderPostgresDAO implements OrderDAO {
  private static final String UPDATE_NUMBER_QUERY = "UPDATE %s SET last_number = last_number + 1 RETURNING last_number";
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

  @Override
  public Future<Long> getNextPoNumber(Conn conn) {
    String sql = String.format(UPDATE_NUMBER_QUERY, ORDER_NUMBER_TABLE);
    return conn.execute(sql)
      .map(rowSet -> {
        if (rowSet.rowCount() == 0) {
          log.error("getNextPoNumber:: Could not get a new purchase order number (rowCount is 0); sql: {}",sql);
          throw new HttpException(500, "Could not get a new purchase order number (rowCount is 0)");
        }
        Row row = rowSet.iterator().next();
        return row.get(Long.class, 0);
      });
  }
}
