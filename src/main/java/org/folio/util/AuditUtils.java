package org.folio.util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.rest.jaxrs.model.Metadata;

import io.vertx.core.json.JsonObject;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AuditUtils {

  public static String buildTopicName(String envId, String tenantId, String eventType) {
    return KafkaTopicNameHelper.formatTopicName(envId, KafkaTopicNameHelper.getDefaultNameSpace(), tenantId, eventType);
  }

  public static Metadata getMetadataOrThrow(Supplier<Metadata> metadataGetter, Supplier<String> idGetter) {
    return Optional.ofNullable(metadataGetter.get())
      .orElseThrow(() -> new IllegalArgumentException("Metadata is null for entity with id: %s".formatted(idGetter.get())));
  }

  @Nullable
  public static <T, R> R convertToSnapshot(T entity, BiFunction<T, Metadata, T> medatadaSetter, Class<R> snapshotClass) {
    return Optional.ofNullable(entity)
      .map(e -> medatadaSetter.apply(e, null))
      .map(JsonObject::mapFrom)
      .map(json -> json.mapTo(snapshotClass))
      .orElse(null);
  }

}
