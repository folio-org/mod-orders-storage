package org.folio.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryEventType {

  INVENTORY_ITEM_CREATE("inventory.item", EventType.CREATE),
  INVENTORY_HOLDING_CREATE("inventory.holdings-record", EventType.CREATE);

  private final String topicName;
  private final EventType eventType;

}
