package org.folio.event.handler;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.event.dto.ResourceEvent;
import org.folio.okapi.common.XOkapiHeaders;

import java.util.Objects;

import static org.folio.event.EventType.UPDATE;

public class TestHandlerUtil {

  static final String DIKU_TENANT = "diku";
  static final String OKAPI_URL = "http://okapi:9130";
  static final String KAFKA_TOPIC = "dummy.topic";
  static final String RECORD_KEY = "dummy_key";
  static final int PARTITION = 1;
  static final int OFFSET = 1;

  private TestHandlerUtil() {
  }

  static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent, String tenantId) {
    String recordValue;
    if (Objects.nonNull(resourceEvent)) {
      recordValue = Json.encode(resourceEvent);
    } else {
      recordValue = new JsonObject().encode();
    }
    var consumerRecord = new ConsumerRecord<>(KAFKA_TOPIC, PARTITION, OFFSET, RECORD_KEY, recordValue);
    if (Objects.nonNull(tenantId)) {
      consumerRecord.headers().add(new RecordHeader(XOkapiHeaders.URL, OKAPI_URL.getBytes()));
      consumerRecord.headers().add(new RecordHeader(XOkapiHeaders.TENANT, DIKU_TENANT.getBytes()));
    }
    return new KafkaConsumerRecordImpl<>(consumerRecord);
  }

  static ResourceEvent createDefaultUpdateResourceEvent() {
    return ResourceEvent.builder()
      .type(UPDATE)
      .tenant(DIKU_TENANT)
      .newValue(new JsonObject())
      .oldValue(new JsonObject())
      .build();
  }

  static ResourceEvent extractResourceEvent(KafkaConsumerRecord<String, String> record) {
    return Json.decodeValue(record.value(), ResourceEvent.class);
  }
}
