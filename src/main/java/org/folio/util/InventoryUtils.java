package org.folio.util;

import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;
import org.folio.rest.jaxrs.model.Contributor;

import java.util.List;

@Log4j2
public class InventoryUtils {

  public static final String INSTANCE_ID = "id";
  public static final String INSTANCE_TITLE = "title";
  public static final String INSTANCE_PUBLISHER = "publisher";
  public static final String INSTANCE_CONTRIBUTORS = "contributors";
  public static final String INSTANCE_DATE_OF_PUBLICATION = "dateOfPublication";
  public static final String INSTANCE_PUBLICATION = "publication";
  public static final String CONTRIBUTOR_NAME = "name";
  public static final String CONTRIBUTOR_NAME_TYPE_ID = "contributorNameTypeId";

  public static final String HOLDING_ID = "id";
  public static final String HOLDING_INSTANCE_ID = "instanceId";
  public static final String HOLDING_PERMANENT_LOCATION_ID = "permanentLocationId";

  private InventoryUtils() {
  }

  public static String getInstanceTitle(JsonObject instance) {
    return instance.getString(INSTANCE_TITLE);
  }

  public static String getPublisher(JsonObject instance) {
    var publication = instance.getJsonArray(INSTANCE_PUBLICATION);
    if (publication == null || publication.isEmpty()) {
      return null;
    }
    return publication.getJsonObject(0).getString(INSTANCE_PUBLISHER);
  }

  public static String getPublicationDate(JsonObject instance) {
    var publication = instance.getJsonArray(INSTANCE_PUBLICATION);
    if (publication == null || publication.isEmpty()) {
      return null;
    }
    return publication.getJsonObject(0).getString(INSTANCE_DATE_OF_PUBLICATION);
  }

  public static List<Contributor> getContributors(JsonObject instance) {
    var contributors = instance.getJsonArray(INSTANCE_CONTRIBUTORS);
    if (contributors == null || contributors.isEmpty()) {
      return List.of();
    }
    return contributors
      .stream()
      .map(JsonObject.class::cast)
      .map(jsonObject -> new Contributor()
        .withContributor(jsonObject.getString(CONTRIBUTOR_NAME))
        .withContributorNameTypeId(jsonObject.getString(CONTRIBUTOR_NAME_TYPE_ID)))
      .toList();
  }
}
