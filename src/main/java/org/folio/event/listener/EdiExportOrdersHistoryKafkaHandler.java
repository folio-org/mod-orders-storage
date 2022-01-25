package org.folio.event.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.kafka.AsyncRecordHandler;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

@Component
public class EdiExportOrdersHistoryKafkaHandler implements AsyncRecordHandler<String, String> {

  public static final String JOB_PROFILE_SNAPSHOT_ID_KEY = "JOB_PROFILE_SNAPSHOT_ID";
  private static final String RECORD_ID_HEADER = "recordId";
  private static final String CHUNK_ID_HEADER = "chunkId";
  private static final String JOB_EXECUTION_ID_HEADER = "jobExecutionId";
  private static final String PROFILE_SNAPSHOT_NOT_FOUND_MSG = "JobProfileSnapshot was not found by id '%s'";

  private final Logger logger = LogManager.getLogger(EdiExportOrdersHistoryKafkaHandler.class);

  public EdiExportOrdersHistoryKafkaHandler() {

  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> kafkaRecord) {
//    try {
//      Promise<String> promise = Promise.promise();
//      Event event = DatabindCodec.mapper().readValue(kafkaRecord.value(), Event.class);
//      DataImportEventPayload eventPayload = Json.decodeValue(event.getEventPayload(), DataImportEventPayload.class);
//      String recordId = extractValueFromHeaders(kafkaRecord.headers(), RECORD_ID_HEADER);
//      String chunkId = extractValueFromHeaders(kafkaRecord.headers(), CHUNK_ID_HEADER);
//      String jobExecutionId = extractValueFromHeaders(kafkaRecord.headers(), JOB_EXECUTION_ID_HEADER);
//
//      logger.info("Data import event payload has been received with event type: {}, jobExecutionId: {}, recordId: {}, chunkId: {}", eventPayload.getEventType(), jobExecutionId, recordId, chunkId);
//      eventPayload.getContext().put(RECORD_ID_HEADER, recordId);
//      populateContextWithOkapiUserAndPerms(kafkaRecord, eventPayload);
//
//      String profileSnapshotId = eventPayload.getContext().get(JOB_PROFILE_SNAPSHOT_ID_KEY);
//      Map<String, String> okapiHeaders = DataImportUtils.getOkapiHeaders(eventPayload);
//
//      profileSnapshotCache.get(profileSnapshotId, okapiHeaders)
//        .thenCompose(snapshotOptional -> snapshotOptional
//          .map(profileSnapshot -> EventManager.handleEvent(eventPayload, profileSnapshot))
//          .orElse(CompletableFuture.failedFuture(new EventProcessingException(format(PROFILE_SNAPSHOT_NOT_FOUND_MSG, profileSnapshotId)))))
//        .whenComplete((processedPayload, throwable) -> {
//          if (throwable != null) {
//            promise.fail(throwable);
//          } else if (DI_ERROR.value().equals(processedPayload.getEventType())) {
//            promise.fail("Failed to process data import event payload");
//          } else {
//            promise.complete(kafkaRecord.key());
//          }
//        });
//      return promise.future();
//    } catch (IOException e) {
//      logger.error("Failed to process data import kafka record from topic {}", kafkaRecord.topic(), e);
//      return Future.failedFuture(e);
//    }
      return Future.succeededFuture("Done");
  }

//  private void populateContextWithOkapiUserAndPerms(KafkaConsumerRecord<String, String> kafkaRecord,
//                                                    DataImportEventPayload eventPayload) {
//    for (KafkaHeader header: kafkaRecord.headers()) {
//      if (UserPermissionsUtil.OKAPI_HEADER_PERMISSIONS.equalsIgnoreCase(header.key())) {
//        String permissions = header.value().toString();
//        eventPayload.getContext().put(DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_PERMISSIONS, permissions);
//      } else if (RestVerticle.OKAPI_USERID_HEADER.equalsIgnoreCase(header.key())) {
//        String userId = header.value().toString();
//        eventPayload.getContext().put(DataImportUtils.DATA_IMPORT_PAYLOAD_OKAPI_USER_ID, userId);
//      }
//    }
//  }
//
//  private String extractValueFromHeaders(List<KafkaHeader> headers, String key) {
//    return headers.stream()
//      .filter(header -> header.key().equals(key))
//      .findFirst()
//      .map(header -> header.value().toString())
//      .orElse(null);
//  }
}
