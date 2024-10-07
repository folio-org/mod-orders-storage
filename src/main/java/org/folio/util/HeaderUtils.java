package org.folio.util;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import java.util.Map;

import io.vertx.core.MultiMap;
import lombok.extern.log4j.Log4j;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.okapi.common.XOkapiHeaders;

@Log4j
public final class HeaderUtils {

  public static MultiMap convertToCaseInsensitiveMultiMap(Map<String, String> okapiHeaders) {
    return MultiMap.caseInsensitiveMultiMap()
      .addAll(okapiHeaders)
      .add("Accept", APPLICATION_JSON + ", " + TEXT_PLAIN); // set default Accept header
  }

  public static CaseInsensitiveMap<String, String> prepareHeaderForTenant(String tenantId, Map<String, String> headers) {
    CaseInsensitiveMap<String, String> headersCopy = new CaseInsensitiveMap<>(headers);
    headersCopy.put(XOkapiHeaders.TENANT, tenantId);
    return headersCopy;
  }
}
