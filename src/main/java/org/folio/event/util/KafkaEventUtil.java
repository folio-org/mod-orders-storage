package org.folio.event.util;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import io.vertx.kafka.client.producer.KafkaHeader;

import java.util.List;
import java.util.Optional;

public final class KafkaEventUtil {
  public static final String TENANT_NOT_SPECIFIED_MSG = "Tenant must be specified in the kafka record " + OKAPI_HEADER_TENANT;

  private KafkaEventUtil() {

  }

  public static String extractTenantFromHeaders(List<KafkaHeader> headers) {
    return Optional.ofNullable(KafkaEventUtil.extractValueFromHeaders(headers, OKAPI_HEADER_TENANT))
      .orElseThrow(() -> new IllegalStateException(TENANT_NOT_SPECIFIED_MSG));
  }

  public static String extractValueFromHeaders(List<KafkaHeader> headers, String key) {
    return headers.stream()
      .filter(header -> header.key().equalsIgnoreCase(key))
      .findFirst()
      .map(header -> header.value().toString())
      .orElse(null);
  }

}
