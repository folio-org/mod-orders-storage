package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.CUSTOM_FIELDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.util.UUID;

import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomField.Type;
import org.folio.rest.jaxrs.model.CustomFieldSection;
import org.folio.rest.jaxrs.model.CustomFieldSectionCollection;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.utils.IsolatedTenant;
import org.junit.jupiter.api.Test;

import io.restassured.http.Headers;
import io.vertx.core.json.Json;

/**
 * Smoke test of the host wiring of the folio-custom-fields sections (schema.json snippet, RMB routing).
 * The section logic itself is covered by the library.
 */
@IsolatedTenant
public class CustomFieldSectionsAPITest extends TestBase {

  private static final String SECTIONS_ENDPOINT = "/custom-field-sections";
  private static final String SECTIONS_ENDPOINT_WITH_ID = SECTIONS_ENDPOINT + "/{id}";
  private static final String PURCHASE_ORDER_ENTITY_TYPE = "purchase_order";
  private static final String PO_LINE_ENTITY_TYPE = "po_line";

  @Test
  void testCreateAndListSection() throws MalformedURLException {
    CustomFieldSection section = createSection("Vendor information", PURCHASE_ORDER_ENTITY_TYPE);

    CustomFieldSectionCollection sections =
      getData(SECTIONS_ENDPOINT + "?query=entityType==" + PURCHASE_ORDER_ENTITY_TYPE, ISOLATED_TENANT_HEADER)
        .then()
        .statusCode(200)
        .extract()
        .as(CustomFieldSectionCollection.class);

    assertEquals(1, sections.getTotalRecords());
    assertEquals(section.getId(), sections.getCustomFieldSections().getFirst().getId());
  }

  @Test
  void testAssignSectionAndDeleteInUseSection() throws MalformedURLException {
    CustomFieldSection section = createSection("Contact", PURCHASE_ORDER_ENTITY_TYPE);

    CustomField poField = postData(CUSTOM_FIELDS.getEndpoint(),
      Json.encode(textField("poContact", PURCHASE_ORDER_ENTITY_TYPE, section.getId())),
      new Headers(ISOLATED_TENANT_HEADER))
      .then()
      .statusCode(201)
      .extract()
      .as(CustomField.class);
    assertEquals(section.getId(), poField.getSectionId());

    // a section can only hold fields of its own entity type
    postData(CUSTOM_FIELDS.getEndpoint(),
      Json.encode(textField("polContact", PO_LINE_ENTITY_TYPE, section.getId())),
      new Headers(ISOLATED_TENANT_HEADER))
      .then()
      .statusCode(422);

    Errors errors = deleteData(SECTIONS_ENDPOINT_WITH_ID, section.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(422)
      .extract()
      .as(Errors.class);
    assertEquals("sectionInUse", errors.getErrors().getFirst().getCode());

    putData(CUSTOM_FIELDS.getEndpointWithId(), poField.getId(), Json.encode(poField.withSectionId(null)),
      new Headers(ISOLATED_TENANT_HEADER))
      .then()
      .statusCode(204);

    deleteData(SECTIONS_ENDPOINT_WITH_ID, section.getId(), ISOLATED_TENANT_HEADER)
      .then()
      .statusCode(204);
  }

  private CustomFieldSection createSection(String name, String entityType) throws MalformedURLException {
    return postData(SECTIONS_ENDPOINT,
      Json.encode(new CustomFieldSection().withName(name).withEntityType(entityType)),
      new Headers(ISOLATED_TENANT_HEADER))
      .then()
      .statusCode(201)
      .extract()
      .as(CustomFieldSection.class);
  }

  private CustomField textField(String name, String entityType, String sectionId) {
    return new CustomField()
      .withId(UUID.randomUUID().toString())
      .withName(name)
      .withType(Type.TEXTBOX_SHORT)
      .withEntityType(entityType)
      .withSectionId(sectionId);
  }
}
