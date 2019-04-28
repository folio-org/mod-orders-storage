package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.Context;
import org.folio.HttpStatus;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

import java.net.URL;
import java.util.Map;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.impl.StorageTestSuite.storageUrl;
import static org.mockito.ArgumentMatchers.any;

public class HelperUtilsTest extends TestBase {

  private static final String EXCEPTIONAL_METHOD_NAME = "postgresClient";
  private static final String ORDERS_ENDPOINT = "/orders-storage/orders";
  private static final String RECEIVING_HISTORY_ENDPOINT = "/orders-storage/receiving-history";

  @Test
  public void getEntitiesCollectionWithDistinctOnFailNpExTest() throws Exception {
    PowerMockito.spy(PgUtil.class);
    PowerMockito.doThrow(new NullPointerException()).when(PgUtil.class, EXCEPTIONAL_METHOD_NAME, any(Context.class), any(Map.class));
    get(storageUrl(ORDERS_ENDPOINT)).statusCode(HttpStatus.HTTP_BAD_REQUEST.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  public void getEntitiesCollectionWithDistinctOnFailCqlExTest() throws Exception {
    PowerMockito.spy(PgUtil.class);
    PowerMockito.doThrow(new CQLQueryValidationException(null)).when(PgUtil.class, EXCEPTIONAL_METHOD_NAME, any(Context.class), any(Map.class));
    get(storageUrl(ORDERS_ENDPOINT)).statusCode(HttpStatus.HTTP_BAD_REQUEST.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  public void getEntitiesCollectionFailTest() throws Exception {
    PowerMockito.spy(PgUtil.class);
    PowerMockito.doThrow(new CQLQueryValidationException(null)).when(PgUtil.class, EXCEPTIONAL_METHOD_NAME, any(Context.class), any(Map.class));
    get(storageUrl(RECEIVING_HISTORY_ENDPOINT)).statusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  public void entitiesMetadataHolderRespond400FailTest() throws Exception {
    EntitiesMetadataHolder holder = PowerMockito.mock(EntitiesMetadataHolder.class);
    PowerMockito.doThrow(new NoSuchMethodException()).when(holder).getRespond400WithTextPlainMethod();
    PowerMockito.whenNew(EntitiesMetadataHolder.class).withAnyArguments().thenReturn(holder);
    get(storageUrl(ORDERS_ENDPOINT)).statusCode(HttpStatus.HTTP_BAD_REQUEST.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  public void entitiesMetadataHolderRespond200FailTest() throws Exception {
    EntitiesMetadataHolder holder = PowerMockito.mock(EntitiesMetadataHolder.class);
    PowerMockito.doThrow(new NoSuchMethodException()).when(holder).getRespond200WithApplicationJson();
    PowerMockito.whenNew(EntitiesMetadataHolder.class).withAnyArguments().thenReturn(holder);
    get(storageUrl(ORDERS_ENDPOINT)).statusCode(HttpStatus.HTTP_BAD_REQUEST.toInt()).contentType(TEXT_PLAIN);
  }

  private ValidatableResponse get(URL endpoint) {
    return RestAssured
      .with()
        .header(TENANT_HEADER)
        .get(endpoint)
          .then();
  }
}
