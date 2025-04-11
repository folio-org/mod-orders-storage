package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.TestUtils.PURCHASE_METHOD;
import static org.folio.rest.utils.TestEntities.CUSTOM_FIELDS;
import static org.folio.rest.utils.TestEntities.ORDER_TEMPLATE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.Cost;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomField.Type;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.jaxrs.model.CustomFields;
import org.folio.rest.jaxrs.model.OrderTemplate;
import org.folio.rest.jaxrs.model.OrderTemplateCollection;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLine.OrderFormat;
import org.folio.rest.jaxrs.model.PoLine.Source;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.PurchaseOrder.OrderType;
import org.folio.rest.jaxrs.model.PurchaseOrderCollection;
import org.folio.rest.jaxrs.model.PutCustomFieldCollection;
import org.folio.rest.jaxrs.model.SelectField;
import org.folio.rest.jaxrs.model.SelectFieldOption;
import org.folio.rest.jaxrs.model.SelectFieldOptions;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestEntities;
import org.junit.jupiter.api.Test;

import io.restassured.http.Headers;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@IsolatedTenant
public class CustomFieldsAPITest extends TestBase {

  private static final String STATS_ENDPOINT = "/custom-fields/{id}/stats";
  private static final String OPTIONS_STATS_ENDPOINT = "/custom-fields/{id}/options/{optId}/stats";
  private List<CustomField> customFieldsPO;
  private List<CustomField> customFieldsPOL;
  private List<PurchaseOrder> purchaseOrders;
  private List<PoLine> poLines;
  private List<OrderTemplate> orderTemplates;

  @Test
  void testCustomFieldStatisticCount() {
    populateSampleData();

    assertEquals(2, getCustomFieldStatisticCount(customFieldsPO.get(0)));
    assertEquals(3, getCustomFieldStatisticCount(customFieldsPOL.get(0)));
  }

  @Test
  void testCustomFieldOptionFieldStatisticCount() {
    populateSampleData();

    assertEquals(2, getCustomFieldOptionStatisticCount(customFieldsPO.get(1), "opt_0"));
    assertEquals(3, getCustomFieldOptionStatisticCount(customFieldsPOL.get(2), "opt_2"));
  }

  @Test
  void testDeleteCustomField() {
    populateSampleData();

    // delete custom field PO
    deleteCustomField(customFieldsPO.get(0));

    // POs should be updated
    Map<String, PurchaseOrder> allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertEquals(2, purchaseOrder1.getCustomFields().getAdditionalProperties().size());
    assertNull(purchaseOrder1.getCustomFields().getAdditionalProperties().get("textbox"));
    assertEquals(2, purchaseOrder2.getCustomFields().getAdditionalProperties().size());
    assertNull(purchaseOrder2.getCustomFields().getAdditionalProperties().get("textbox"));

    // POLs remain unchanged
    Map<String, PoLine> allPoLines = getAllPoLines();
    PoLine poLine1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2 = allPoLines.get(poLines.get(1).getId());
    assertThat(poLine1, samePropertyValuesAs(poLines.get(0)));
    assertThat(poLine2, samePropertyValuesAs(poLines.get(1)));

    // order template remains unchanged
    assertThat(
      getAllOrderTemplates().get(orderTemplates.get(0).getId()),
      samePropertyValuesAs(orderTemplates.get(0)));

    // delete custom field POL
    deleteCustomField(customFieldsPOL.get(0));

    // POs remain unchanged
    allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1_1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2_1 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertThat(purchaseOrder1_1, samePropertyValuesAs(purchaseOrder1));
    assertThat(purchaseOrder2_1, samePropertyValuesAs(purchaseOrder2));

    // POLs should be updated
    allPoLines = getAllPoLines();
    PoLine poLine1_1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2_1 = allPoLines.get(poLines.get(1).getId());
    assertEquals(2, poLine1_1.getCustomFields().getAdditionalProperties().size());
    assertNull(poLine1_1.getCustomFields().getAdditionalProperties().get("textbox_2"));
    assertEquals(2, poLine2_1.getCustomFields().getAdditionalProperties().size());
    assertNull(poLine2_1.getCustomFields().getAdditionalProperties().get("textbox_2"));

    // order template should be updated
    Map<String, OrderTemplate> allOrderTemplates = getAllOrderTemplates();
    OrderTemplate orderTemplate = allOrderTemplates.get(orderTemplates.get(0).getId());
    JsonObject customFieldData = getCustomFieldData(orderTemplate);
    assertEquals(4, customFieldData.size());
    assertNull(customFieldData.getValue("textbox"));
  }

  @Test
  void testPutCustomFieldRemoveSingleOption() {
    populateSampleData();

    // remove single option from custom field PO
    customFieldsPO.get(1).getSelectField().getOptions().getValues().remove(0);
    putCustomField(customFieldsPO.get(1));

    // POs should be updated
    Map<String, PurchaseOrder> allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertEquals(2, purchaseOrder1.getCustomFields().getAdditionalProperties().size());
    assertNull(purchaseOrder1.getCustomFields().getAdditionalProperties().get("singleselect"));
    assertThat(purchaseOrder2, samePropertyValuesAs(purchaseOrders.get(1)));

    // POLs remain unchanged
    Map<String, PoLine> allPoLines = getAllPoLines();
    PoLine poLine1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2 = allPoLines.get(poLines.get(1).getId());
    assertThat(poLine1, samePropertyValuesAs(poLines.get(0)));
    assertThat(poLine2, samePropertyValuesAs(poLines.get(1)));

    // order template should be updated
    Map<String, OrderTemplate> allOrderTemplates = getAllOrderTemplates();
    OrderTemplate orderTemplate = allOrderTemplates.get(orderTemplates.get(0).getId());
    JsonObject customFieldData = getCustomFieldData(orderTemplate);
    assertEquals(4, customFieldData.size());
    assertNull(customFieldData.getValue("singleselect"));

    // remove single option from custom field POL
    customFieldsPOL.get(1).getSelectField().getOptions().getValues().remove(0);
    putCustomField(customFieldsPOL.get(1));

    // POs should remain unchanged
    allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1_1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2_1 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertThat(purchaseOrder1_1, samePropertyValuesAs(purchaseOrder1));
    assertThat(purchaseOrder2_1, samePropertyValuesAs(purchaseOrder2));

    // one POL should be updated
    allPoLines = getAllPoLines();
    PoLine poLine1_1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2_1 = allPoLines.get(poLines.get(1).getId());
    assertEquals(2, poLine1_1.getCustomFields().getAdditionalProperties().size());
    assertNull(poLine1_1.getCustomFields().getAdditionalProperties().get("singleselect_2"));
    assertThat(poLine2_1, samePropertyValuesAs(poLines.get(1)));

    // order template should be updated
    allOrderTemplates = getAllOrderTemplates();
    orderTemplate = allOrderTemplates.get(orderTemplates.get(0).getId());
    customFieldData = getCustomFieldData(orderTemplate);
    assertEquals(3, customFieldData.size());
    assertNull(customFieldData.getValue("singleselect_2"));
  }

  @Test
  void testPutCustomFieldRemoveMultipleOptions() {
    populateSampleData();

    // remove multiple options from custom field PO
    customFieldsPO
      .get(2)
      .getSelectField()
      .getOptions()
      .getValues()
      .removeIf(opt -> List.of("opt_2", "opt_3").contains(opt.getId()));
    putCustomField(customFieldsPO.get(2));

    // POs should be updated
    Map<String, PurchaseOrder> allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertEquals(3, purchaseOrder1.getCustomFields().getAdditionalProperties().size());
    assertEquals(
      List.of("opt_1"),
      purchaseOrder1.getCustomFields().getAdditionalProperties().get("multiselect"));
    assertEquals(2, purchaseOrder2.getCustomFields().getAdditionalProperties().size());
    assertNull(purchaseOrder2.getCustomFields().getAdditionalProperties().get("multiselect"));

    // POLs remain unchanged
    Map<String, PoLine> allPoLines = getAllPoLines();
    PoLine poLine1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2 = allPoLines.get(poLines.get(1).getId());
    assertThat(poLine1, samePropertyValuesAs(poLines.get(0)));
    assertThat(poLine2, samePropertyValuesAs(poLines.get(1)));

    // order template should be updated
    Map<String, OrderTemplate> allOrderTemplates = getAllOrderTemplates();
    OrderTemplate orderTemplate = allOrderTemplates.get(orderTemplates.get(0).getId());
    JsonObject customFieldData = getCustomFieldData(orderTemplate);
    assertEquals(5, customFieldData.size());
    assertEquals(new JsonArray().add("opt_1"), customFieldData.getValue("multiselect"));

    // remove multiple options from custom field POL
    customFieldsPOL
      .get(2)
      .getSelectField()
      .getOptions()
      .getValues()
      .removeIf(opt -> List.of("opt_2", "opt_3").contains(opt.getId()));
    putCustomField(customFieldsPOL.get(2));

    // POs remain unchanged
    allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1_1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2_1 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertThat(purchaseOrder1_1, samePropertyValuesAs(purchaseOrder1));
    assertThat(purchaseOrder2_1, samePropertyValuesAs(purchaseOrder2));

    // POLs should be updated
    allPoLines = getAllPoLines();
    PoLine poLine1_1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2_1 = allPoLines.get(poLines.get(1).getId());
    assertEquals(3, poLine1_1.getCustomFields().getAdditionalProperties().size());
    assertEquals(
      List.of("opt_1"),
      poLine1_1.getCustomFields().getAdditionalProperties().get("multiselect_2"));
    assertEquals(2, poLine2_1.getCustomFields().getAdditionalProperties().size());
    assertNull(poLine2_1.getCustomFields().getAdditionalProperties().get("multiselect_2"));

    // order template should be updated
    allOrderTemplates = getAllOrderTemplates();
    orderTemplate = allOrderTemplates.get(orderTemplates.get(0).getId());
    customFieldData = getCustomFieldData(orderTemplate);
    assertEquals(5, customFieldData.size());
    assertEquals(new JsonArray().add("opt_1"), customFieldData.getValue("multiselect_2"));
  }

  @Test
  void testPutPutCustomFieldCollection() {
    // populate with sample data
    populateSampleData();

    // remove opt from custom field using PutCustomFieldsCollection
    customFieldsPO.get(1).getSelectField().getOptions().getValues().remove(0);
    putCustomFieldCollection(
      new PutCustomFieldCollection()
        .withCustomFields(customFieldsPO)
        .withEntityType("purchase_order"));

    // POs should be updated
    Map<String, PurchaseOrder> allPurchaseOrders = getAllPurchaseOrders();
    PurchaseOrder purchaseOrder1 = allPurchaseOrders.get(purchaseOrders.get(0).getId());
    PurchaseOrder purchaseOrder2 = allPurchaseOrders.get(purchaseOrders.get(1).getId());
    assertEquals(2, purchaseOrder1.getCustomFields().getAdditionalProperties().size());
    assertNull(purchaseOrder1.getCustomFields().getAdditionalProperties().get("singleselect"));
    assertEquals(3, purchaseOrder2.getCustomFields().getAdditionalProperties().size());
    assertThat(purchaseOrder2, samePropertyValuesAs(purchaseOrders.get(1)));

    // POLs remain unchanged
    Map<String, PoLine> allPoLines = getAllPoLines();
    PoLine poLine1 = allPoLines.get(poLines.get(0).getId());
    PoLine poLine2 = allPoLines.get(poLines.get(1).getId());
    assertThat(poLine1, samePropertyValuesAs(poLines.get(0)));
    assertThat(poLine2, samePropertyValuesAs(poLines.get(1)));

    // order template should be updated
    Map<String, OrderTemplate> allOrderTemplates = getAllOrderTemplates();
    OrderTemplate orderTemplate = allOrderTemplates.get(orderTemplates.get(0).getId());
    JsonObject customFieldData = getCustomFieldData(orderTemplate);
    assertEquals(4, customFieldData.size());
    assertNull(customFieldData.getValue("singleselect"));
  }

  private void populateSampleData() {
    customFieldsPO = createCustomFields("purchase_order");
    customFieldsPOL = createCustomFields("po_line");
    purchaseOrders = createPOs();
    poLines = createPOLs(purchaseOrders);
    orderTemplates = createOrderTemplates();
  }

  private List<CustomField> createCustomFields(String entityType) {
    CustomField textbox =
      new CustomField()
        .withId(UUID.randomUUID().toString())
        .withName("textbox")
        .withType(Type.TEXTBOX_SHORT)
        .withEntityType(entityType);
    CustomField singleselect =
      new CustomField()
        .withId(UUID.randomUUID().toString())
        .withName("singleselect")
        .withType(Type.SINGLE_SELECT_DROPDOWN)
        .withSelectField(
          new SelectField()
            .withMultiSelect(false)
            .withOptions(
              new SelectFieldOptions()
                .withValues(
                  Arrays.asList(
                    new SelectFieldOption().withId("opt_0").withValue("opt0"),
                    new SelectFieldOption().withId("opt_1").withValue("opt1"),
                    new SelectFieldOption().withId("opt_2").withValue("opt2")))))
        .withEntityType(entityType);
    CustomField multiselect =
      new CustomField()
        .withId(UUID.randomUUID().toString())
        .withName("multiselect")
        .withType(Type.MULTI_SELECT_DROPDOWN)
        .withSelectField(
          new SelectField()
            .withMultiSelect(true)
            .withOptions(
              new SelectFieldOptions()
                .withValues(
                  Arrays.asList(
                    new SelectFieldOption().withId("opt_0").withValue("opt0"),
                    new SelectFieldOption().withId("opt_1").withValue("opt1"),
                    new SelectFieldOption().withId("opt_2").withValue("opt2"),
                    new SelectFieldOption().withId("opt_3").withValue("opt3")))))
        .withEntityType(entityType);

    return postEntities(CUSTOM_FIELDS, List.of(textbox, singleselect, multiselect));
  }

  private List<PurchaseOrder> createPOs() {
    PurchaseOrder purchaseOrder1 =
      new PurchaseOrder()
        .withId(UUID.randomUUID().toString())
        .withPoNumber("1111")
        .withCustomFields(
          new CustomFields()
            .withAdditionalProperty("textbox", "text1")
            .withAdditionalProperty("singleselect", "opt_0")
            .withAdditionalProperty(
              "multiselect", new JsonArray().add("opt_1").add("opt_2")));

    PurchaseOrder purchaseOrder2 =
      new PurchaseOrder()
        .withId(UUID.randomUUID().toString())
        .withPoNumber("2222")
        .withVendor(UUID.randomUUID().toString())
        .withOrderType(OrderType.ONE_TIME)
        .withCustomFields(
          new CustomFields()
            .withAdditionalProperty("textbox", "text2")
            .withAdditionalProperty("singleselect", "opt_2")
            .withAdditionalProperty(
              "multiselect", new JsonArray().add("opt_2").add("opt_3")));

    return postEntities(PURCHASE_ORDER, List.of(purchaseOrder1, purchaseOrder2));
  }

  private List<PoLine> createPOLs(List<PurchaseOrder> purchaseOrders) {
    PoLine poLine1 =
      new PoLine()
        .withId(UUID.randomUUID().toString())
        .withAcquisitionMethod(PURCHASE_METHOD)
        .withOrderFormat(OrderFormat.OTHER)
        .withSource(Source.USER)
        .withPurchaseOrderId(purchaseOrders.get(0).getId())
        .withCost((new Cost()).withCurrency("USD").withQuantityPhysical(1).withListUnitPrice(1d))
        .withTitleOrPackage("title1")
        .withCustomFields(
          new CustomFields()
            .withAdditionalProperty("textbox_2", "text1")
            .withAdditionalProperty("singleselect_2", "opt_0")
            .withAdditionalProperty(
              "multiselect_2", new JsonArray().add("opt_1").add("opt_2")));

    PoLine poLine2 =
      new PoLine()
        .withId(UUID.randomUUID().toString())
        .withAcquisitionMethod(PURCHASE_METHOD)
        .withOrderFormat(OrderFormat.OTHER)
        .withSource(Source.USER)
        .withPurchaseOrderId(purchaseOrders.get(1).getId())
        .withCost((new Cost()).withCurrency("USD").withQuantityPhysical(1).withListUnitPrice(1d))
        .withTitleOrPackage("title2")
        .withCustomFields(
          new CustomFields()
            .withAdditionalProperty("textbox_2", "text2")
            .withAdditionalProperty("singleselect_2", "opt_2")
            .withAdditionalProperty(
              "multiselect_2", new JsonArray().add("opt_2").add("opt_3")));

    return postEntities(PO_LINE, List.of(poLine1, poLine2));
  }

  private List<OrderTemplate> createOrderTemplates() {
    OrderTemplate orderTemplate =
      new OrderTemplate()
        .withId(UUID.randomUUID().toString())
        .withTemplateName("template1")
        .withAdditionalProperty(
          "customFields",
          new JsonObject()
            .put("singleselect", "opt_0")
            .put("multiselect", new JsonArray().add("opt_1").add("opt_2"))
            .put("textbox_2", "defaulttext")
            .put("singleselect_2", "opt_0")
            .put("multiselect_2", new JsonArray().add("opt_1").add("opt_2")));

    return postEntities(ORDER_TEMPLATE, List.of(orderTemplate));
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> postEntities(TestEntities testEntity, List<T> entities) {
    ArrayList<T> results = new ArrayList<>();
    entities.forEach(
      entity -> {
        try {
          T createdEntity =
            postData(
              testEntity.getEndpoint(),
              Json.encode(entity),
              new Headers(ISOLATED_TENANT_HEADER))
              .then()
              .statusCode(201)
              .extract()
              .as((Class<T>) testEntity.getClazz());
          results.add(createdEntity);
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      });
    return results;
  }

  private void putCustomField(CustomField customField) {
    try {
      putData(
        CUSTOM_FIELDS.getEndpointWithId(),
        customField.getId(),
        Json.encode(customField),
        new Headers(ISOLATED_TENANT_HEADER))
        .then()
        .statusCode(204);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private void putCustomFieldCollection(PutCustomFieldCollection putCustomFieldCollection) {
    try {
      given()
        .header(ISOLATED_TENANT_HEADER)
        .header("Content-Type", "application/json")
        .body(Json.encode(putCustomFieldCollection))
        .put(storageUrl(CUSTOM_FIELDS.getEndpoint()))
        .then()
        .statusCode(204);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private void deleteCustomField(CustomField customField) {
    try {
      deleteData(CUSTOM_FIELDS.getEndpointWithId(), customField.getId(), ISOLATED_TENANT_HEADER)
        .then()
        .statusCode(204);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, PurchaseOrder> getAllPurchaseOrders() {
    try {
      return getData(PURCHASE_ORDER.getEndpoint(), ISOLATED_TENANT_HEADER)
        .then()
        .statusCode(200)
        .extract()
        .as(PurchaseOrderCollection.class)
        .getPurchaseOrders()
        .stream()
        .collect(Collectors.toMap(PurchaseOrder::getId, Function.identity()));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonObject getCustomFieldData(Object entity) {
    return JsonObject.mapFrom(entity).getJsonObject("customFields");
  }

  private Map<String, OrderTemplate> getAllOrderTemplates() {
    try {
      return getData(ORDER_TEMPLATE.getEndpoint(), ISOLATED_TENANT_HEADER)
        .then()
        .statusCode(200)
        .extract()
        .as(OrderTemplateCollection.class)
        .getOrderTemplates()
        .stream()
        .collect(Collectors.toMap(OrderTemplate::getId, Function.identity()));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, PoLine> getAllPoLines() {
    try {
      return getData(PO_LINE.getEndpoint(), ISOLATED_TENANT_HEADER)
        .then()
        .statusCode(200)
        .extract()
        .as(PoLineCollection.class)
        .getPoLines()
        .stream()
        .collect(Collectors.toMap(PoLine::getId, Function.identity()));
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private int getCustomFieldOptionStatisticCount(CustomField customField, String optionId) {
    try {
      return given()
        .header(ISOLATED_TENANT_HEADER)
        .pathParams("id", customField.getId(), "optId", optionId)
        .get(storageUrl(OPTIONS_STATS_ENDPOINT))
        .then()
        .statusCode(200)
        .extract()
        .as(CustomFieldOptionStatistic.class)
        .getCount();
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private int getCustomFieldStatisticCount(CustomField customField) {
    try {
      return given()
        .header(ISOLATED_TENANT_HEADER)
        .pathParams("id", customField.getId())
        .get(storageUrl(STATS_ENDPOINT))
        .then()
        .statusCode(200)
        .extract()
        .as(CustomFieldStatistic.class)
        .getCount();
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
