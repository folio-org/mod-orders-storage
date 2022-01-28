package org.folio.event;

import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.impl.KafkaHeaderImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class KafkaEventUtilTest {

  @Test
  void successRetrieveHeader() {
    String expValue = "diku";
    KafkaHeader kafkaHeader = new KafkaHeaderImpl("x-okapi-header", expValue);
    String actValue = KafkaEventUtil.extractValueFromHeaders(List.of(kafkaHeader), "x-okapi-header");
    Assertions.assertEquals(expValue, actValue);
  }

  @Test
  void ifNoFoundThenReturnNull() {
    String expValue = "diku";
    KafkaHeader kafkaHeader = new KafkaHeaderImpl("x-okapi-header1", expValue);
    String actValue = KafkaEventUtil.extractValueFromHeaders(List.of(kafkaHeader), "x-okapi-header");
    Assertions.assertNull(actValue);
  }
}
