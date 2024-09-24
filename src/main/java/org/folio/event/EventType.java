package org.folio.event;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {
  UPDATE("UPDATE"),
  CREATE("CREATE"),
  DELETE("DELETE"),
  DELETE_ALL("DELETE_ALL");

  @JsonValue
  private final String value;
}
