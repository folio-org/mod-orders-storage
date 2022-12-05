package org.folio.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum AuditEventType {
  ACQ_ORDER_CHANGED("ACQ_ORDER_CHANGED"),
  ACQ_ORDER_LINE_CHANGED("ACQ_ORDER_LINE_CHANGED");

  private final String topicName;
}
