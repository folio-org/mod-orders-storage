package org.folio.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.Contributor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.folio.event.dto.InstanceFields.CONTRIBUTOR_NAME;
import static org.folio.event.dto.InstanceFields.CONTRIBUTOR_NAME_TYPE_ID;
import static org.folio.event.dto.InstanceFields.CONTRIBUTORS;
import static org.folio.event.dto.InstanceFields.DATE_OF_PUBLICATION;
import static org.folio.event.dto.InstanceFields.IDENTIFIERS;
import static org.folio.event.dto.InstanceFields.IDENTIFIER_TYPE_ID;
import static org.folio.event.dto.InstanceFields.IDENTIFIER_TYPE_VALUE;
import static org.folio.event.dto.InstanceFields.PUBLICATION;
import static org.folio.event.dto.InstanceFields.PUBLISHER;
import static org.folio.event.dto.InstanceFields.TITLE;
import static org.junit.jupiter.api.Assertions.*;

@CopilotGenerated(partiallyGenerated = true)
class InventoryUtilsTest {

  @Test
  void testGetInstanceTitle() {
    var instance = new JsonObject().put(TITLE.getValue(), "Title");
    assertEquals("Title", InventoryUtils.getInstanceTitle(instance));
  }

  @Test
  void testGetPublisher() {
    var instance = new JsonObject().put(PUBLICATION.getValue(),
      new JsonArray().add(new JsonObject().put(PUBLISHER.getValue(), "Publisher")));
    assertEquals("Publisher", InventoryUtils.getPublisher(instance));
  }

  @Test
  void testGetPublisherNull() {
    var instance = new JsonObject();
    assertNull(InventoryUtils.getPublisher(instance));
  }

  @Test
  void testGetPublicationDate() {
    var instance = new JsonObject().put(PUBLICATION.getValue(),
      new JsonArray().add(new JsonObject().put(DATE_OF_PUBLICATION.getValue(), "2023-01-01")));
    assertEquals("2023-01-01", InventoryUtils.getPublicationDate(instance));
  }

  @Test
  void testGetPublicationDateNull() {
    var instance = new JsonObject();
    assertNull(InventoryUtils.getPublicationDate(instance));
  }

  @Test
  void testGetContributors() {
    var instance = new JsonObject().put(CONTRIBUTORS.getValue(),
      new JsonArray().add(new JsonObject()
        .put(CONTRIBUTOR_NAME.getValue(), "Contributor")
        .put(CONTRIBUTOR_NAME_TYPE_ID.getValue(), "1")));
    var contributors = InventoryUtils.getContributors(instance);
    assertEquals(1, contributors.size());
    assertEquals("Contributor", contributors.get(0).getContributor());
    assertEquals("1", contributors.get(0).getContributorNameTypeId());
  }

  @Test
  void testGetContributorsEmpty() {
    var instance = new JsonObject();
    List<Contributor> contributors = InventoryUtils.getContributors(instance);
    assertTrue(contributors.isEmpty());
  }

  @Test
  void testGetProductIds() {
    var instance = new JsonObject().put(IDENTIFIERS.getValue(),
      new JsonArray().add(new JsonObject()
        .put(IDENTIFIER_TYPE_VALUE.getValue(), "Id")
        .put(IDENTIFIER_TYPE_ID.getValue(), "1")));
    var productIds = InventoryUtils.getProductIds(instance);
    Assertions.assertEquals(1, productIds.size());
    Assertions.assertEquals("Id", productIds.get(0).getProductId());
    Assertions.assertEquals("1", productIds.get(0).getProductIdType());
  }

  @Test
  void testGetProductIdsEmpty() {
    var instance = new JsonObject();
    var productIds = InventoryUtils.getProductIds(instance);
    Assertions.assertTrue(productIds.isEmpty());
  }
}
