package org.folio.rest.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.folio.models.TableNames;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.utils.IsolatedTenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@IsolatedTenant
public class AbstractApiHandlerTest extends TestBase {
  private AbstractApiHandler abstractApiHandler;
  private PostgresClient postgresClient;

  @BeforeEach
  public void initMocks() {
    postgresClient = PostgresClient.getInstance(Vertx.vertx(), ISOLATED_TENANT_HEADER.getValue());
    abstractApiHandler = new AbstractApiHandler(postgresClient) {
      @Override
      String getEndpoint(Object entity) {
        return "orders-storage/purchase-order";
      }
    };
  }


  @Test
  public void testShouldReturn500IfProblemWithConnection() {
    Tx<String> tx = new Tx<>("111", postgresClient);

    abstractApiHandler.deleteByQuery(tx, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper(), false)
      .onComplete(asyncResult -> {
        if (asyncResult.failed()) {
          HttpStatusException exception = (HttpStatusException) asyncResult.cause();
          assertEquals(500, exception.getStatusCode());
        }
      });
  }

  @Test
  public void testShouldReturnNotFoundExceptionIfSilentIsFalse() {
    String orderId = UUID.randomUUID().toString();
    Tx<String> tx = new Tx<>(orderId, postgresClient);
    tx.startTx()
      .onSuccess(startedTr -> {
        abstractApiHandler.deleteByQuery(startedTr, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper(), false)
          .onComplete(asyncResult -> {
            if (asyncResult.failed()) {
              HttpStatusException exception = (HttpStatusException) asyncResult.cause();
              assertEquals(400, exception.getStatusCode());
            }
          });
      });
  }

  @Test
  public void testShouldReturnTransactionIfSilentIsTrue() {
    String orderId = UUID.randomUUID().toString();
    Tx<String> tx = new Tx<>(orderId, postgresClient);
    tx.startTx()
      .onSuccess(startedTr -> {
        abstractApiHandler.deleteByQuery(startedTr, TableNames.PURCHASE_ORDER_TABLE, new CQLWrapper(), true)
          .onComplete(asyncResult -> {
            if (!asyncResult.failed()) {
              assertEquals(orderId, asyncResult.result().getEntity());
            }
          });
      });
  }
}
