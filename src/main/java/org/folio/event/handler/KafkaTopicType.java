package org.folio.event.handler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum KafkaTopicType {
  EXPORT_HISTORY_CREATE("edi-export-history.create");

  private final String topicName;
}
