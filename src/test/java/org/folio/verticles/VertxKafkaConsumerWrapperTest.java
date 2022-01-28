package org.folio.verticles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.kafka.AsyncRecordHandler;
import org.folio.kafka.KafkaConsumerWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordImpl;

public class VertxKafkaConsumerWrapperTest {
  private VertxKafkaConsumerWrapper<?, ?> vertxKafkaConsumerWrapper;
  private final KafkaConsumerWrapper<?, ?> kafkaConsumerWrapper = mock(KafkaConsumerWrapper.class);
  private final AsyncRecordHandler<?, ?> asyncRecordHandler = mock(AsyncRecordHandler.class);

  @BeforeEach
  public void init() {
    vertxKafkaConsumerWrapper = new VertxKafkaConsumerWrapper(kafkaConsumerWrapper, asyncRecordHandler);
  }

  @Test
  void shouldDelegateStartToCoreWrapper() {
    String moduleName = "mod-orders-storage-2.1.1";
    doReturn(Future.succeededFuture()).when(kafkaConsumerWrapper).start(any(AsyncRecordHandler.class),eq(moduleName));
    //When
    vertxKafkaConsumerWrapper.start(moduleName).result();
    verify(kafkaConsumerWrapper, times(1)).start(any(AsyncRecordHandler.class), eq(moduleName));
  }

  @Test
  void shouldDelegateHandleToCoreWrapper() {
    var consumerRecord = new ConsumerRecord("topik", 1, 1, "key", "value");
    var record = new KafkaConsumerRecordImpl(consumerRecord) ;
    doNothing().when(kafkaConsumerWrapper).handle(record);
    //When
    vertxKafkaConsumerWrapper.handle(record);
    verify(kafkaConsumerWrapper, times(1)).handle(record);
  }

  @Test
  void shouldDelegateStopToCoreWrapper() {
    String moduleName = "mod-orders-storage-2.1.1";
    doReturn(Future.succeededFuture()).when(kafkaConsumerWrapper).stop();
    //When
    vertxKafkaConsumerWrapper.stop().result();
    verify(kafkaConsumerWrapper, times(1)).stop();
  }

}
