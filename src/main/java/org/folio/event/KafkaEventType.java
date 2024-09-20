package org.folio.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum KafkaEventType {
  EXPORT_HISTORY_CREATE("edi-export-history.create"),
  INVENTORY_ITEM_CREATE("inventory.item"),;

  private final String topicName;
}
