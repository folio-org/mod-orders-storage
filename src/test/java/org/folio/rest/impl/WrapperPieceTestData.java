package org.folio.rest.impl;

import io.restassured.http.Headers;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode
class WrapperPieceTestData {
  private String userId;
  private Headers headers;
  private String vendorId;
  private String purchaseOrderId;
  private String poLineId;
  private String titleId;
  private List<String> pieceIds;
}
