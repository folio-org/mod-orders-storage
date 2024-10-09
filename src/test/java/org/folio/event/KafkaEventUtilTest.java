package org.folio.event;

import java.util.List;

import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.impl.KafkaHeaderImpl;
import org.folio.util.HeaderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KafkaEventUtilTest {

  @Test
  void successRetrieveHeader() {
    String expValue = "diku";
    KafkaHeader kafkaHeader = new KafkaHeaderImpl("x-okapi-header", expValue);
    String actValue = HeaderUtils.extractValueFromHeaders(List.of(kafkaHeader), "x-okapi-header");
    Assertions.assertEquals(expValue, actValue);
  }

  @Test
  void ifNoFoundThenReturnNull() {
    String expValue = "diku";
    KafkaHeader kafkaHeader = new KafkaHeaderImpl("x-okapi-header1", expValue);
    String actValue = HeaderUtils.extractValueFromHeaders(List.of(kafkaHeader), "x-okapi-header");
    Assertions.assertNull(actValue);
  }
}
