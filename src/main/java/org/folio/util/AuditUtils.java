package org.folio.util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.folio.kafka.KafkaTopicNameHelper;
import org.folio.rest.jaxrs.model.Metadata;

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
  public static <T> T withNullMetadata(T entity, BiFunction<T, Metadata, T> setter) {
    return Optional.ofNullable(entity).map(e -> setter.apply(e, null)).orElse(null);
  }

}
