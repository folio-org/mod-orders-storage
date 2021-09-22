package org.folio.migration.models.dto;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "fundCode"
})

public class FundCodeMigrationDto {

  @JsonProperty("id")
  @JsonPropertyDescription("Fund ID")
  @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
  private String id;

  @JsonProperty("code")
  @JsonPropertyDescription("Fund code")
  @Valid
  private String code;

  public FundCodeMigrationDto(String id) {
    this.id = id;
  }

  @JsonProperty("id")
  public String getId() {
    return id;
  }

  @JsonProperty("id")
  public void setId(String id) {
    this.id = id;
  }

  public FundCodeMigrationDto withId(String id) {
    this.id = id;
    return this;
  }

  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  @JsonProperty("code")
  public void setCode(String code) {
    this.code = code;
  }

  public FundCodeMigrationDto withCode(String code) {
    this.code = code;
    return this;
  }

}
