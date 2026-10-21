package org.folio.event.dto;

import java.util.List;
import java.util.function.Function;

import one.util.streamex.StreamEx;

public record AuditEntityWrapper<T, P> (T entity, T originalEntity, P parentEntity) {

  public static <T, P> AuditEntityWrapper<T, P> of(T entity, T originalEntity) {
    return new AuditEntityWrapper<>(entity, originalEntity, null);
  }

  public static <T, P> AuditEntityWrapper<T, P> of(T entity) {
    return new AuditEntityWrapper<>(entity, null, null);
  }

  public static <T, P> List<AuditEntityWrapper<T, P>> listOf(List<T> entities) {
    return entities.stream().map(AuditEntityWrapper::<T, P>of).toList();
  }

  public static <T, P> List<AuditEntityWrapper<T, P>> listOf(List<T> entities, List<T> originalEntities, Function<T, String> entityIdExtractor) {
    var originalEntitiesMap = StreamEx.of(originalEntities).toMap(entityIdExtractor, Function.identity());
    return entities.stream()
      .map(entity -> AuditEntityWrapper.<T, P>of(entity, originalEntitiesMap.get(entityIdExtractor.apply(entity))))
      .toList();
  }

  public AuditEntityWrapper<T, P> withParentEntity(P parentEntity) {
    return new AuditEntityWrapper<>(entity, originalEntity, parentEntity);
  }

}
