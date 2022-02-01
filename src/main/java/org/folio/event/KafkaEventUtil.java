package org.folio.event;

import io.vertx.kafka.client.producer.KafkaHeader;

import java.util.List;

public final class KafkaEventUtil {

  private KafkaEventUtil() {

  }

  public static String extractValueFromHeaders(List<KafkaHeader> headers, String key) {
    return headers.stream()
      .filter(header -> header.key().equals(key))
      .findFirst()
      .map(header -> header.value().toString())
      .orElse(null);
  }
}
