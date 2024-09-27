package org.folio.event.util;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import io.vertx.kafka.client.producer.KafkaHeader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class KafkaEventUtil {
  private static final String TENANT_NOT_SPECIFIED_MSG = "Tenant must be specified in the kafka record " + OKAPI_HEADER_TENANT;

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

  public static Map<String, String> getHeaderMap(List<KafkaHeader> headers) {
    return headers.stream()
      .collect(Collectors.toMap(KafkaHeader::key, header -> header.value().toString()));
  }

}
