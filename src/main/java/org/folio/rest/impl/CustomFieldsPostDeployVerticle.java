package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.PostDeployVerticle;

/**
 * Shadows the {@code CustomFieldsPostDeployVerticle} class from {@code folio-custom-fields} with a
 * NoOp implementation. This class may be removed if <a
 * href="https://issues.folio.org/browse/FCFIELDS-42">FCFIELDS-42</a> is resolved.
 */
public class CustomFieldsPostDeployVerticle implements PostDeployVerticle {
  private static final Logger log = LogManager.getLogger();

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    log.info("Using NoOp implementation of CustomFieldsPostDeployVerticle");
    handler.handle(Future.succeededFuture(true));
  }
}
