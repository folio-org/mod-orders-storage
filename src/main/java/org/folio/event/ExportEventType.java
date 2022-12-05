package org.folio.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ExportEventType {
  EXPORT_HISTORY_CREATE("edi-export-history.create");

  private final String topicName;
}
