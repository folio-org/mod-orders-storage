package org.folio.util;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Metadata;

public class MetadataUtils {

  private MetadataUtils() { }

  public static <T> T populateMetadata(Supplier<Metadata> metadataExtractor, Function<Metadata, T> metadataSetter, Map<String, String> okapiHeaders) {
    var userId =  okapiHeaders.get(XOkapiHeaders.USER_ID);
    var metadata = Optional.ofNullable(metadataExtractor.get()).orElseGet(Metadata::new);
    metadata.setUpdatedDate(new Date());
    metadata.setUpdatedByUserId(userId);
    if (metadata.getCreatedDate() == null && metadata.getCreatedByUserId() == null) {
      metadata.setCreatedDate(metadata.getUpdatedDate());
      metadata.setCreatedByUserId(userId);
    }
    return metadataSetter.apply(metadata);
  }

}
