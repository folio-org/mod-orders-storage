package org.folio.event.dto;

import java.util.List;
import java.util.function.Function;

import one.util.streamex.StreamEx;

public record AuditEntityWrapper<T> (T entity, T originalEntity) {

  public static <T> AuditEntityWrapper<T> of(T entity, T originalEntity) {
    return new AuditEntityWrapper<>(entity, originalEntity);
  }

  public static <T> AuditEntityWrapper<T> of(T entity) {
    return new AuditEntityWrapper<>(entity, null);
  }

  public static <T> List<AuditEntityWrapper<T>> listOf(List<T> entities) {
    return entities.stream().map(AuditEntityWrapper::of).toList();
  }

  public static <T> List<AuditEntityWrapper<T>> listOf(List<T> entities, List<T> originalEntities, Function<T, String> entityIdExtractor) {
    var originalEntitiesMap = StreamEx.of(originalEntities).toMap(entityIdExtractor, Function.identity());
    return entities.stream()
      .map(entity -> AuditEntityWrapper.of(entity, originalEntitiesMap.get(entityIdExtractor.apply(entity))))
      .toList();
  }

}
