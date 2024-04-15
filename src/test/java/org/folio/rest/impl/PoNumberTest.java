package org.folio.rest.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.net.MalformedURLException;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.order.OrderPostgresDAO;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.persist.Conn;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;


public class PoNumberTest extends TestBase {
  private static final Logger log = LogManager.getLogger();
  private static final String PO_NUMBER_ENDPOINT = "/orders-storage/po-number";

  @Mock
  private Conn conn;
  @Mock
  private RowSet<Row> rowSet;

  @Test
  public void testGetPoNumberOk() throws MalformedURLException {

    long poNumber1 = getPoNumberAsInt();
    log.info("--- mod-orders-storage Generated po_number1: " + poNumber1);
    long poNumber2 = getPoNumberAsInt();
    log.info("--- mod-orders-storage Generated po_number2: " + poNumber2);
    long poNumber3 = getPoNumberAsInt();
    log.info("--- mod-orders-storage Generated po_number3: " + poNumber3);

    //ensure that the numbers returned are in fact sequential
    assertEquals(1, poNumber3 - poNumber2);
    assertEquals(1, poNumber2 - poNumber1);
  }

  @Test
  public void testError() throws Exception {
    AutoCloseable mockitoMocks = MockitoAnnotations.openMocks(this);
    OrderPostgresDAO orderPostgresDAO = new OrderPostgresDAO();
    doReturn(0).when(rowSet).rowCount();
    doReturn(Future.succeededFuture(rowSet)).when(conn).execute(anyString());
    Future<Long> f = orderPostgresDAO.getNextPoNumber(conn);
    assertThat(f.failed(), is(true));
    HttpException thrown = (HttpException)f.cause();
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), thrown.getCode());
    mockitoMocks.close();
  }

  private int getPoNumberAsInt() throws MalformedURLException {
    return Integer.parseInt(getData(PO_NUMBER_ENDPOINT)
      .then()
      .statusCode(200)
      .extract()
      .response()
      .path("sequenceNumber"));
  }
}
