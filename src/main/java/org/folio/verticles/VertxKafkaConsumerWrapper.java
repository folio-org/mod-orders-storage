package org.folio.verticles;

import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaConsumerWrapper;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class VertxKafkaConsumerWrapper<K, V> implements Handler<KafkaConsumerRecord<K, V>> {

  private final KafkaConsumerWrapper<K, V> kafkaConsumerWrapper;
  private final AsyncRecordHandler<K, V> asyncRecordHandler;

  public VertxKafkaConsumerWrapper(KafkaConsumerWrapper<K, V> kafkaConsumerWrapper,
                                   AsyncRecordHandler<K, V> asyncRecordHandler) {
    this.kafkaConsumerWrapper = kafkaConsumerWrapper;
    this.asyncRecordHandler = asyncRecordHandler;
  }

  @Override
  public void handle(KafkaConsumerRecord<K, V> event) {
    kafkaConsumerWrapper.handle(event);
  }

  protected Future<Void> start(String moduleName) {
    return kafkaConsumerWrapper.start(asyncRecordHandler, moduleName);
  }

  protected Future<Void> stop() {
    return kafkaConsumerWrapper.stop();
  }
}
