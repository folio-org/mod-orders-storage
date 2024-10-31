package org.folio.event.handler;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.event.dto.ResourceEvent;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;

import java.util.Arrays;
import java.util.Objects;

import static org.folio.event.EventType.UPDATE;


public class TestHandlerUtil {

  static final String OKAPI_URL = "http://okapi:9130";
  static final String KAFKA_TOPIC = "dummy.topic";
  static final String RECORD_KEY = "dummy_key";
  static final int PARTITION = 1;
  static final int OFFSET = 1;
  static final String CONSORTIUM_ID = "consortiumId";
  static final String DIKU_TENANT = "diku";
  static final String CENTRAL_TENANT = "central";
  static final String UNIVERSITY_TENANT = "university";
  static final String COLLEGE_TENANT = "college";

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

  static ResourceEvent extractResourceEvent(KafkaConsumerRecord<String, String> kafkaRecord) {
    return Json.decodeValue(kafkaRecord.value(), ResourceEvent.class);
  }

  static Setting createSetting(String value) {
    return new Setting().withValue(value);
  }

  static SettingCollection createSettingCollection(Setting... settings) {
    return new SettingCollection()
      .withSettings(Arrays.stream(settings).toList())
      .withTotalRecords(1);
  }

  public static KafkaConsumerRecord<String, String> createKafkaRecordWithValues(JsonObject oldItemValue, JsonObject newItemValue) {
    var resourceEvent = createDefaultUpdateResourceEvent();
    resourceEvent.setOldValue(oldItemValue);
    resourceEvent.setNewValue(newItemValue);
    return createKafkaRecord(resourceEvent, DIKU_TENANT);
  }
}
