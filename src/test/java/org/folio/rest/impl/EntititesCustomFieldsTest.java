package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.CUSTOM_FIELDS;
import static org.folio.rest.utils.TestEntitiesCustomFields.ORDER_TEMPLATE;
import static org.folio.rest.utils.TestEntitiesCustomFields.PO_LINE;
import static org.folio.rest.utils.TestEntitiesCustomFields.PO_LINE_PACKAGE;
import static org.folio.rest.utils.TestEntitiesCustomFields.PURCHASE_ORDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import java.net.MalformedURLException;
import java.util.List;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.folio.rest.utils.TestData.CustomFields;
import org.junit.jupiter.api.Test;

@IsolatedTenant
public class EntititesCustomFieldsTest extends TestBase {
  private static final Header header = ISOLATED_TENANT_HEADER;
  private static final Headers headers = new Headers(header);

  private final String customFieldPurchaseOrder = getFile(CustomFields.PO);
  private final String customFieldPoLine = getFile(CustomFields.POL);

  void setupCustomFields() throws MalformedURLException {
    postData(CUSTOM_FIELDS.getEndpoint(), customFieldPurchaseOrder, headers).then().statusCode(201);
    postData(CUSTOM_FIELDS.getEndpoint(), customFieldPoLine, headers).then().statusCode(201);
  }

  void setupOrder() throws MalformedURLException {
    postData(
            PURCHASE_ORDER.getEndpoint(),
            PURCHASE_ORDER.getSampleEntity().encode(),
            headers)
        .then()
        .statusCode(201);
  }

  void setupOrderLine() throws MalformedURLException {
    postData(PO_LINE.getEndpoint(), PO_LINE.getSampleEntity().encode(), headers)
        .then()
        .statusCode(201);
  }

  void setupOrderTemplate() throws MalformedURLException {
    postData(ORDER_TEMPLATE.getEndpoint(), ORDER_TEMPLATE.getSampleEntity().encode(), headers)
      .then()
      .statusCode(201);
  }

  private void assertThatResponseContainsErrorWithMessage(
      Response response, String containsMessage) {
    List<Error> errors =
        response
            .then()
            .log()
            .all()
            .statusCode(422)
            .contentType(APPLICATION_JSON)
            .extract()
            .as(Errors.class)
            .getErrors();
    assertEquals(1, errors.size());
    assertTrue(errors.get(0).getMessage().contains(containsMessage));
  }

  @Test
  void testPostOrderWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    postData(
            PURCHASE_ORDER.getEndpoint(),
            PURCHASE_ORDER.getSampleEntityWithValidCustomFields().encode(),
            headers)
        .then()
        .statusCode(201);
    verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), 1, header);
  }

  @Test
  void testPostOrderWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    Response response =
        postData(
            PURCHASE_ORDER.getEndpoint(),
            PURCHASE_ORDER.getSampleEntityWithInvalidCustomFields().encode(),
            headers);
    assertThatResponseContainsErrorWithMessage(response, PURCHASE_ORDER.getErrorContainsMessage());
    verifyCollectionQuantity(PURCHASE_ORDER.getEndpoint(), 0, header);
  }

  @Test
  void testPutOrderWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    putData(
            PURCHASE_ORDER.getEndpointWithId(),
            PURCHASE_ORDER.getId(),
            PURCHASE_ORDER.getSampleEntityWithValidCustomFields().encode(),
            headers)
        .then()
        .statusCode(204);
  }

  @Test
  void testPutOrderWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    Response response =
        putData(
            PURCHASE_ORDER.getEndpointWithId(),
            PURCHASE_ORDER.getId(),
            PURCHASE_ORDER.getSampleEntityWithInvalidCustomFields().encode(),
            headers);
    assertThatResponseContainsErrorWithMessage(response, PURCHASE_ORDER.getErrorContainsMessage());
  }

  @Test
  void testPostOrderLineWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    postData(
            PO_LINE.getEndpoint(),
            PO_LINE.getSampleEntityWithValidCustomFields().encode(),
            headers)
        .then()
        .statusCode(201);
    verifyCollectionQuantity(PO_LINE.getEndpoint(), 1, header);
  }

  @Test
  void testPostOrderLineWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    Response response =
        postData(
            PO_LINE.getEndpoint(),
            PO_LINE.getSampleEntityWithInvalidCustomFields().encode(),
            headers);
    assertThatResponseContainsErrorWithMessage(response, PO_LINE.getErrorContainsMessage());
    verifyCollectionQuantity(PO_LINE.getEndpoint(), 0, header);
  }

  @Test
  void testPutOrderLineWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    setupOrderLine();
    putData(
            PO_LINE.getEndpointWithId(),
            PO_LINE.getId(),
            PO_LINE.getSampleEntityWithValidCustomFields().encode(),
            headers)
        .then()
        .statusCode(204);
  }

  @Test
  void testPutOrderLineWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    Response response =
        putData(
            PO_LINE.getEndpointWithId(),
            PO_LINE.getId(),
            PO_LINE.getSampleEntityWithInvalidCustomFields().encode(),
            headers);
    assertThatResponseContainsErrorWithMessage(response, PO_LINE.getErrorContainsMessage());
  }

  @Test
  void testPostOrderLinePackageWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    postData(
            PO_LINE_PACKAGE.getEndpoint(),
            PO_LINE_PACKAGE.getSampleEntityWithValidCustomFields().encode(),
            headers)
        .then()
        .statusCode(201);
    verifyCollectionQuantity(PO_LINE_PACKAGE.getEndpoint(), 1, header);
  }

  @Test
  void testPostOrderLinePackageWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    Response response =
        postData(
            PO_LINE_PACKAGE.getEndpoint(),
            PO_LINE_PACKAGE.getSampleEntityWithInvalidCustomFields().encode(),
            headers);
    assertThatResponseContainsErrorWithMessage(response, PO_LINE_PACKAGE.getErrorContainsMessage());
    verifyCollectionQuantity(PO_LINE_PACKAGE.getEndpoint(), 0, header);
  }

  @Test
  void testPutOrderLinePackageWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    setupOrderLine();
    putData(
            PO_LINE_PACKAGE.getEndpointWithId(),
            PO_LINE_PACKAGE.getId(),
            PO_LINE_PACKAGE.getSampleEntityWithValidCustomFields().encode(),
            headers)
        .then()
        .statusCode(204);
  }

  @Test
  void testPutOrderLinePackageWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrder();
    setupOrderLine();
    Response response =
        putData(
            PO_LINE_PACKAGE.getEndpointWithId(),
            PO_LINE_PACKAGE.getId(),
            PO_LINE_PACKAGE.getSampleEntityWithInvalidCustomFields().encode(),
            headers);
    assertThatResponseContainsErrorWithMessage(response, PO_LINE_PACKAGE.getErrorContainsMessage());
  }

  @Test
  void testPutOrderLineBatchWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    PoLineCollection poLineCollection =
        new PoLineCollection()
            .withPoLines(
                List.of(
                    getFileAsObject(TestData.PoLine.DEFAULT_81, PoLine.class),
                    PO_LINE.getSampleEntityWithInvalidCustomFields().mapTo(PoLine.class)))
            .withTotalRecords(1);
    Response response =
        given()
            .headers(headers)
            .contentType(ContentType.JSON)
            .body(poLineCollection)
            .put(storageUrl("/orders-storage/po-lines-batch"));
    assertThatResponseContainsErrorWithMessage(response, PO_LINE.getErrorContainsMessage());
  }

  @Test
  void testPostOrderTemplateWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    postData(
      ORDER_TEMPLATE.getEndpoint(),
      ORDER_TEMPLATE.getSampleEntityWithValidCustomFields().encode(),
      headers)
      .then()
      .statusCode(201);
    verifyCollectionQuantity(ORDER_TEMPLATE.getEndpoint(), 1, header);
  }

  @Test
  void testPostOrderTemplateWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    Response response =
      postData(
        ORDER_TEMPLATE.getEndpoint(),
        ORDER_TEMPLATE.getSampleEntityWithInvalidCustomFields().encode(),
        headers);
    assertThatResponseContainsErrorWithMessage(response, ORDER_TEMPLATE.getErrorContainsMessage());
    verifyCollectionQuantity(ORDER_TEMPLATE.getEndpoint(), 0, header);
  }

  @Test
  void testPutOrderTemplateWithValidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrderTemplate();
    putData(
      ORDER_TEMPLATE.getEndpointWithId(),
      ORDER_TEMPLATE.getId(),
      ORDER_TEMPLATE.getSampleEntityWithValidCustomFields().encode(),
      headers)
      .then()
      .statusCode(204);
  }

  @Test
  void testPutOrderTemplateWithInvalidCustomFields() throws MalformedURLException {
    setupCustomFields();
    setupOrderTemplate();
    Response response =
      putData(
        ORDER_TEMPLATE.getEndpointWithId(),
        ORDER_TEMPLATE.getId(),
        ORDER_TEMPLATE.getSampleEntityWithInvalidCustomFields().encode(),
        headers);
    assertThatResponseContainsErrorWithMessage(response, ORDER_TEMPLATE.getErrorContainsMessage());
  }
}
