package org.folio.event.handler;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;
import lombok.experimental.UtilityClass;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.event.EventType;
import org.folio.event.dto.ResourceEvent;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.folio.event.EventType.UPDATE;

@UtilityClass
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

  static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent) {
    return createKafkaRecord(resourceEvent, List.of());
  }

  static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent, String tenantId) {
    return createKafkaRecord(resourceEvent, tenantId, List.of());
  }

  static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent, String tenantId, List<Pair<String, String>> headerPairs) {
    List<Pair<String, String>> defaultHeaders = StringUtils.isNotBlank(tenantId)
      ? List.of(Pair.of(XOkapiHeaders.URL, OKAPI_URL), Pair.of(XOkapiHeaders.TENANT, tenantId))
      : List.of();
    return createKafkaRecord(resourceEvent, ListUtils.union(defaultHeaders, Optional.ofNullable(headerPairs).orElseGet(List::of)));
  }

  static KafkaConsumerRecord<String, String> createKafkaRecord(ResourceEvent resourceEvent, List<Pair<String, String>> headerPairs) {
    var recordValue = Objects.nonNull(resourceEvent) ? Json.encode(resourceEvent) : new JsonObject().encode();
    var consumerRecord = new ConsumerRecord<>(KAFKA_TOPIC, PARTITION, OFFSET, RECORD_KEY, recordValue);
    headerPairs.stream()
      .filter(header -> StringUtils.isNotBlank(header.getKey()) && StringUtils.isNotBlank(header.getValue()))
      .map(header -> new RecordHeader(header.getKey(), header.getValue().getBytes()))
      .forEach(consumerRecord.headers()::add);
    return new KafkaConsumerRecordImpl<>(consumerRecord);
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
    return createKafkaRecord(createDefaultUpdateResourceEvent(DIKU_TENANT, oldItemValue, newItemValue), DIKU_TENANT);
  }

  public static KafkaConsumerRecord<String, String> createKafkaRecordWithValues(JsonObject oldItemValue, JsonObject newItemValue, String tenantId) {
    return createKafkaRecord(createDefaultUpdateResourceEvent(tenantId, oldItemValue, newItemValue), tenantId);
  }

  static ResourceEvent createDefaultUpdateResourceEvent(String tenantId) {
    return createDefaultUpdateResourceEvent(tenantId, new JsonObject(), new JsonObject());
  }

  static ResourceEvent createDefaultUpdateResourceEvent(String tenantId, Object oldValue, Object newValue) {
    var resourceEvent = createResourceEvent(tenantId, UPDATE, newValue);
    resourceEvent.setOldValue(oldValue);
    return resourceEvent;
  }

  static ResourceEvent createResourceEvent(String tenantId, EventType type) {
    return createResourceEvent(tenantId, type, new JsonObject());
  }

  static ResourceEvent createResourceEvent(String tenantId, EventType type, Object newValue) {
    return ResourceEvent.builder()
      .type(type)
      .tenant(tenantId)
      .newValue(newValue)
      .build();
  }

}
