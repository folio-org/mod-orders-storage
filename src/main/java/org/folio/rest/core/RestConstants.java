package org.folio.rest.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RestConstants {
  OKAPI_URL("x-okapi-url");

  private final String value;
}
