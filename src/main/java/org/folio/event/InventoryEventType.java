package org.folio.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public enum InventoryEventType {
  INVENTORY_ITEM_CREATE("inventory.item", EventType.CREATE);

  private String topicName;
  private EventType eventType;
}
