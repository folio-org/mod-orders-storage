package org.folio.services.inventory;

import static org.folio.util.ResourcePath.STORAGE_INSTANCE_URL;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Log4j2
public class InstancesService {

  private final RestClient restClient;

  public Future<JsonObject> getInstanceById(String instanceId, RequestContext requestContext) {
    var requestEntry = new RequestEntry(String.format(STORAGE_INSTANCE_URL.getPath(),instanceId));
    return restClient.get(requestEntry, requestContext);
  }

}
