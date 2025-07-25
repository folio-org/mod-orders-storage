package org.folio.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.experimental.UtilityClass;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.ProductId;

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

@UtilityClass
public class InventoryUtils {

  public static String getInstanceTitle(JsonObject instance) {
    return instance.getString(TITLE.getValue());
  }

  public static String getPublisher(JsonObject instance) {
    var publication = instance.getJsonArray(PUBLICATION.getValue());
    if (publication == null || publication.isEmpty()) {
      return null;
    }
    return publication.getJsonObject(0).getString(PUBLISHER.getValue());
  }

  public static String getPublicationDate(JsonObject instance) {
    var publication = instance.getJsonArray(PUBLICATION.getValue());
    if (publication == null || publication.isEmpty()) {
      return null;
    }
    return publication.getJsonObject(0).getString(DATE_OF_PUBLICATION.getValue());
  }

  public static List<Contributor> getContributors(JsonObject instance) {
    var contributors = instance.getJsonArray(CONTRIBUTORS.getValue());
    if (contributors == null || contributors.isEmpty()) {
      return List.of();
    }
    return contributors
      .stream()
      .map(JsonObject.class::cast)
      .map(jsonObject -> new Contributor()
        .withContributor(jsonObject.getString(CONTRIBUTOR_NAME.getValue()))
        .withContributorNameTypeId(jsonObject.getString(CONTRIBUTOR_NAME_TYPE_ID.getValue())))
      .toList();
  }

  public static List<ProductId> getProductIds(JsonObject instance) {
    JsonArray productIds = instance.getJsonArray(IDENTIFIERS.getValue());
    if (productIds == null || productIds.isEmpty()) {
      return List.of();
    }
    return productIds
      .stream()
      .map(JsonObject.class::cast)
      .map(jsonObject -> new ProductId()
        .withProductId(jsonObject.getString(IDENTIFIER_TYPE_VALUE.getValue()))
        .withProductIdType(jsonObject.getString(IDENTIFIER_TYPE_ID.getValue())))
      .toList();
  }

}
