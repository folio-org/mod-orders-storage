package org.folio.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public enum InventoryEventType {
  INVENTORY_ITEM_CREATE("inventory.item", PayloadType.CREATE);

  private String topicName;
  private PayloadType payloadType;

  public enum PayloadType {
    UPDATE, DELETE, CREATE, DELETE_ALL
  }
}
