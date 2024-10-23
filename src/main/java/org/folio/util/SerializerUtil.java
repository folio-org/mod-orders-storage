package org.folio.util;

import io.vertx.core.json.JsonObject;
import org.folio.dbschema.ObjectMapperTool;

public class SerializerUtil {

  private SerializerUtil() {
  }

  public static JsonObject toJson(Object payload) {
    return new JsonObject(ObjectMapperTool.valueAsString(payload));
  }
}
