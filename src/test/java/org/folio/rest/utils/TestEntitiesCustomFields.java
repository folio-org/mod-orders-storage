package org.folio.rest.utils;

import static io.vertx.core.json.JsonObject.mapFrom;
import static java.util.Objects.requireNonNull;

import io.vertx.core.json.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.model.CustomFields;

@Getter
public enum TestEntitiesCustomFields {
  PURCHASE_ORDER(
    TestEntities.PURCHASE_ORDER,
    new JsonObject(),
    new CustomFields().withAdditionalProperty("externalOrderNumber", "ABC"),
    new CustomFields().withAdditionalProperty("externalOrderNumber", false),
    "must be a string"),
  PO_LINE(
    TestEntities.PO_LINE,
    new JsonObject().put("isPackage", true),
    new CustomFields().withAdditionalProperty("membership", "opt_0"),
    new CustomFields().withAdditionalProperty("membership", "opt_x"),
    "can only have following values"),
  PO_LINE_PACKAGE(
    TestEntities.PO_LINE,
    new JsonObject().put("isPackage", false),
    new CustomFields().withAdditionalProperty("membership", "opt_0"),
    new CustomFields().withAdditionalProperty("membership", "opt_x"),
    "can only have following values"),
  ORDER_TEMPLATE(
    TestEntities.ORDER_TEMPLATE,
    new JsonObject(),
    new CustomFields().withAdditionalProperty("externalOrderNumber", "ABC"),
    new CustomFields().withAdditionalProperty("externalOrderNumber", false),
    "must be a string");

  private final JsonObject sampleEntity;
  private final JsonObject sampleEntityWithValidCustomFields;
  private final JsonObject sampleEntityWithInvalidCustomFields;
  private final String id;
  private final String endpoint;
  private final String endpointWithId;
  private final String errorContainsMessage;

  TestEntitiesCustomFields(
    TestEntities testEntity,
    JsonObject additionalProperties,
    CustomFields validCustomFields,
    CustomFields invalidCustomFields,
    String errorContainsMessage) {
    JsonObject jsonObject;
    try (InputStream inputStream =
      this.getClass().getClassLoader().getResourceAsStream(testEntity.getSampleFileName())) {
      jsonObject =
        new JsonObject(IOUtils.toString(requireNonNull(inputStream), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    this.sampleEntity = jsonObject.copy().mergeIn(additionalProperties);
    this.sampleEntityWithValidCustomFields =
      createEntity(jsonObject, additionalProperties, validCustomFields);
    this.sampleEntityWithInvalidCustomFields =
      createEntity(jsonObject, additionalProperties, invalidCustomFields);
    this.errorContainsMessage = errorContainsMessage;
    this.id = jsonObject.getString("id");
    this.endpoint = testEntity.getEndpoint();
    this.endpointWithId = testEntity.getEndpointWithId();
  }

  private JsonObject createEntity(
    JsonObject jsonObject, JsonObject additionalProperties, CustomFields customFields) {
    return jsonObject
      .copy()
      .mergeIn(additionalProperties)
      .mergeIn(new JsonObject().put("customFields", mapFrom(customFields)));
  }
}
