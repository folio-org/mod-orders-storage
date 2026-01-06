package org.folio.event.dto;


import static org.folio.event.dto.ItemFields.ACCESSION_NUMBER;
import static org.folio.event.dto.ItemFields.BARCODE;
import static org.folio.event.dto.ItemFields.CALL_NUMBER;
import static org.folio.event.dto.ItemFields.EFFECTIVE_CALL_NUMBER_COMPONENTS;
import static org.folio.event.dto.ItemFields.HOLDINGS_RECORD_ID;
import static org.folio.event.dto.ItemFields.ID;

import java.util.Map;
import java.util.Objects;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.util.HeaderUtils;

@Data
@Builder
public class ItemEventHolder {

  private ResourceEvent resourceEvent;
  private Map<String, String> headers;
  private String tenantId;

  private String orderTenantId;
  private String centralTenantId;

  private String itemId;
  private JsonObject item;
  private String holdingId;
  private String barcode;
  private String callNumber;
  private String accessionNumber;
  private Pair<String, String> holdingIdPair;
  private Pair<String, String> barcodePair;
  private Pair<String, String> callNumberPair;
  private Pair<String, String> accessionNumberPair;

  public ItemEventHolder prepareAllIds() {
    var oldValue = JsonObject.mapFrom(resourceEvent.getOldValue());
    var newValue = JsonObject.mapFrom(resourceEvent.getNewValue());
    setItem(newValue);
    setItemId(newValue.getString(ID.getValue()));

    setHoldingIdPair(Pair.of(
      oldValue.getString(HOLDINGS_RECORD_ID.getValue()),
      newValue.getString(HOLDINGS_RECORD_ID.getValue())));
    setBarcodePair(Pair.of(
      oldValue.getString(BARCODE.getValue()),
      newValue.getString(BARCODE.getValue())));
    setCallNumberPair(Pair.of(
      oldValue.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS.getValue()).getString(CALL_NUMBER.getValue()),
      newValue.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS.getValue()).getString(CALL_NUMBER.getValue())));
    setAccessionNumberPair(Pair.of(
      oldValue.getString(ACCESSION_NUMBER.getValue()),
      newValue.getString(ACCESSION_NUMBER.getValue())));

    setHoldingId(holdingIdPair.getRight());
    setBarcode(barcodePair.getRight());
    setCallNumber(callNumberPair.getRight());
    setAccessionNumber(accessionNumberPair.getRight());
    return this;
  }

  public void setOrderTenantId(String orderTenantId) {
    this.orderTenantId = orderTenantId;
    this.headers = HeaderUtils.prepareHeaderForTenant(orderTenantId, headers);
  }

  public boolean isItemRecordUpdated() {
    return !(holdingIdPair.getLeft().equals(holdingIdPair.getRight())
      && barcodePair.getLeft().equals(barcodePair.getRight())
      && callNumberPair.getLeft().equals(callNumberPair.getRight())
      && accessionNumberPair.getLeft().equals(accessionNumberPair.getRight()));
  }

  public String getActiveTenantId() {
    return Objects.nonNull(centralTenantId) ? centralTenantId : tenantId;
  }

}
