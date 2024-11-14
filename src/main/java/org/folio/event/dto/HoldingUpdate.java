package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.folio.rest.jaxrs.model.PoLine;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingUpdate {

  private int affectedRows;
  private boolean isInstanceIdUpdated;
  private boolean isSearchLocationIdsUpdated;
  private List<PoLine> poLinesWithUpdatedInstanceId;
  private List<PoLine> poLinesWithUpdatedSearchLocationIds;
  private List<String> adjacentHoldingIds;
}
