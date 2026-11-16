package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingUpdate {

  private int affectedRows;
  private boolean isInstanceIdUpdated;
  private boolean isSearchLocationIdsUpdated;
  private List<AuditEntityWrapper<PoLine, PurchaseOrder>> poLinesWithUpdatedInstanceId;
  private List<AuditEntityWrapper<PoLine, PurchaseOrder>> poLinesWithUpdatedSearchLocationIds;
  private List<String> adjacentHoldingIds;
}
