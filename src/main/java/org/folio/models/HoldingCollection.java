package org.folio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;


@JsonIgnoreProperties(ignoreUnknown = true)
public @Data
class HoldingCollection {
  @JsonProperty("holdingsRecords")
  private List<Holding> holdingsRecords = new ArrayList<>();

  @JsonProperty("totalRecords")
  private Integer totalRecords;

}
