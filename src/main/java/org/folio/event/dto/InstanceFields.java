package org.folio.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InstanceFields {

  ID("id"),
  TITLE("title"),
  PUBLISHER("publisher"),
  CONTRIBUTORS("contributors"),
  DATE_OF_PUBLICATION("dateOfPublication"),
  PUBLICATION("publication"),
  CONTRIBUTOR_NAME("name"),
  CONTRIBUTOR_NAME_TYPE_ID("contributorNameTypeId"),
  IDENTIFIER_TYPE_ID("identifierTypeId"),
  IDENTIFIERS("identifiers"),
  IDENTIFIER_TYPE_VALUE("value");

  private final String value;
}
