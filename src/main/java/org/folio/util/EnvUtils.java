package org.folio.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EnvUtils {

  public static String getEnvVar(String key, String defaultVal) {
    return System.getenv().getOrDefault(key, defaultVal);
  }

}
