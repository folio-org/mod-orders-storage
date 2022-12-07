package org.folio.event.util;

import io.vertx.kafka.client.producer.KafkaHeader;

import java.util.List;

public final class KafkaEventUtil {

  private KafkaEventUtil() {

  }

  public static String extractValueFromHeaders(List<KafkaHeader> headers, String key) {
    return headers.stream()
      .filter(header -> header.key().equalsIgnoreCase(key))
      .findFirst()
      .map(header -> header.value().toString())
      .orElse(null);
  }
}
