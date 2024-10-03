package org.folio.util;


import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResourcePath {

  USER_TENANTS_ENDPOINT("/user-tenants");

  private static final String PATH_BY_ID = "%s/%s";

  private final String path;

  public String getPathById(String id) {
    return String.format(PATH_BY_ID, path, id);
  }

}
