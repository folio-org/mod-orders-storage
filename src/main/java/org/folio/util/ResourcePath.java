package org.folio.util;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResourcePath {

  USER_TENANTS_ENDPOINT("/user-tenants"),
  STORAGE_HOLDING_URL("/holdings-storage/holdings"),
  STORAGE_BATCH_HOLDING_URL("/holdings-storage/batch/synchronous"),
  STORAGE_INSTANCE_URL("/instance-storage/instances/%s"),
  LOCALE_URL("/locale");

  private final String path;
}
