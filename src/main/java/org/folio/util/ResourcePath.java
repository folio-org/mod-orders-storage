package org.folio.util;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResourcePath {

  USER_TENANTS_ENDPOINT("/user-tenants"),
  STORAGE_HOLDING_URL("/holdings-storage/holdings/%s"),
  STORAGE_BATCH_HOLDING_URL("/holdings-storage/batch/synchronous");

  private final String path;
}
