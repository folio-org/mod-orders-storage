package org.folio.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.Contributor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.folio.util.InventoryUtils.CONTRIBUTOR_NAME;
import static org.folio.util.InventoryUtils.CONTRIBUTOR_NAME_TYPE_ID;
import static org.folio.util.InventoryUtils.INSTANCE_CONTRIBUTORS;
import static org.folio.util.InventoryUtils.INSTANCE_DATE_OF_PUBLICATION;
import static org.folio.util.InventoryUtils.INSTANCE_PUBLICATION;
import static org.folio.util.InventoryUtils.INSTANCE_PUBLISHER;
import static org.folio.util.InventoryUtils.INSTANCE_TITLE;
import static org.junit.jupiter.api.Assertions.*;

@CopilotGenerated(partiallyGenerated = true)
class InventoryUtilsTest {

  @Test
  void testGetInstanceTitle() {
    var instance = new JsonObject().put(INSTANCE_TITLE, "Title");
    assertEquals("Title", InventoryUtils.getInstanceTitle(instance));
  }

  @Test
  void testGetPublisher() {
    var instance = new JsonObject().put(INSTANCE_PUBLICATION,
      new JsonArray().add(new JsonObject().put(INSTANCE_PUBLISHER, "Publisher")));
    assertEquals("Publisher", InventoryUtils.getPublisher(instance));
  }

  @Test
  void testGetPublisherNull() {
    var instance = new JsonObject();
    assertNull(InventoryUtils.getPublisher(instance));
  }

  @Test
  void testGetPublicationDate() {
    var instance = new JsonObject().put(INSTANCE_PUBLICATION,
      new JsonArray().add(new JsonObject().put(INSTANCE_DATE_OF_PUBLICATION, "2023-01-01")));
    assertEquals("2023-01-01", InventoryUtils.getPublicationDate(instance));
  }

  @Test
  void testGetPublicationDateNull() {
    var instance = new JsonObject();
    assertNull(InventoryUtils.getPublicationDate(instance));
  }

  @Test
  void testGetContributors() {
    var instance = new JsonObject().put(INSTANCE_CONTRIBUTORS,
      new JsonArray().add(new JsonObject()
        .put(CONTRIBUTOR_NAME, "Contributor")
        .put(CONTRIBUTOR_NAME_TYPE_ID, "1")));
    var contributors = InventoryUtils.getContributors(instance);
    assertEquals(1, contributors.size());
    assertEquals("Contributor", contributors.get(0).getContributor());
    assertEquals("1", contributors.get(0).getContributorNameTypeId());
  }

  @Test
  void testGetContributorsEmpty() {
    JsonObject instance = new JsonObject();
    List<Contributor> contributors = InventoryUtils.getContributors(instance);
    assertTrue(contributors.isEmpty());
  }
}
