package org.folio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
public @Data
class Holding {

  @JsonProperty("id")
  private String id;

  @JsonProperty("instanceId")
  private String instanceId;

  @JsonProperty("permanentLocationId")
  private String permanentLocationId;
}
