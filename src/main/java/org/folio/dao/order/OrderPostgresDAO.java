package org.folio.dao.order;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.rest.exceptions.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.persist.Conn;
import org.folio.util.DbUtils;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.models.TableNames.ORDER_NUMBER_TABLE;
import static org.folio.models.TableNames.PURCHASE_ORDER_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class OrderPostgresDAO implements OrderDAO {
  private static final String UPDATE_NUMBER_QUERY = "UPDATE %s SET last_number = last_number + 1 RETURNING last_number";
  private static final String ORDERS_BY_IDS_SQL = "SELECT * FROM %s WHERE id = ANY($1);";
  private static final Logger log = LogManager.getLogger();

  @Override
  public Future<PurchaseOrder> getOrderById(String orderId, Conn conn) {
    return conn.getById(PURCHASE_ORDER_TABLE, orderId, PurchaseOrder.class)
      .map(order -> {
        if (order == null) {
          throw new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase());
        }
        return order;
      })
      .onFailure(t -> log.error("getOrderById failed for order with id {}", orderId, t));
  }

  @Override
  public Future<Map<String, PurchaseOrder>> getOrdersByIds(Collection<String> orderIds, String tenantId, Conn conn) {
    if (CollectionUtils.isEmpty(orderIds)) {
      return Future.succeededFuture(Map.of());
    }
    var ids = orderIds.stream().map(UUID::fromString).toArray(UUID[]::new);
    return conn.execute(ORDERS_BY_IDS_SQL.formatted(getFullTableName(tenantId, PURCHASE_ORDER_TABLE)), Tuple.of(ids))
      .map(rows -> DbUtils.getRowSetAsList(rows, PurchaseOrder.class).stream()
        .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity())))
      .onSuccess(orders -> log.debug("getOrdersByIds:: Fetched {} of {} order(s)", orders.size(), ids.length))
      .onFailure(t -> log.error("getOrdersByIds failed for order ids {}", orderIds, t));
  }

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
          log.error("getNextPoNumber:: Could not get a new purchase order number (rowCount is 0); sql: {}", sql);
          throw new HttpException(500, "Could not get a new purchase order number (rowCount is 0)");
        }
        Row row = rowSet.iterator().next();
        return row.get(Long.class, 0);
      });
  }
}
