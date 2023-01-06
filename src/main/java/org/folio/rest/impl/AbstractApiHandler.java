package org.folio.rest.impl;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import io.vertx.pgclient.impl.PgConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.event.service.AuditOutboxService;
import org.folio.rest.core.BaseApi;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.HttpException;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

public abstract class AbstractApiHandler extends BaseApi {
  protected final Logger logger = LogManager.getLogger(this.getClass());

  private final PostgresClient pgClient;

  @Autowired
  private PostgresClientFactory postgresClientFactory;
  @Autowired
  private AuditOutboxService auditOutboxService;

  AbstractApiHandler(String tenantId) {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    pgClient = postgresClientFactory.createInstance(tenantId);
  }

  public  <T> Handler<AsyncResult<Tx<T>>> handleResponseWithLocation(Handler<AsyncResult<Response>> asyncResultHandler, Tx<T> tx, String logMessage, Map<String, String> okapiHeaders) {
    return result -> {
      if (result.failed()) {
        HttpException cause = (HttpException) result.cause();
        logger.error(logMessage, cause, tx.getEntity(), "or associated data failed to be");

        // The result of rollback operation is not so important, main failure cause is used to build the response
        tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
      } else {
        auditOutboxService.processOutboxEventLogs(okapiHeaders)
          .onComplete(ar -> {
            logger.info(logMessage, tx.getEntity(), "and associated data were successfully");
            asyncResultHandler.handle(buildResponseWithLocation(result.result()
                .getEntity(),
              getEndpoint(result.result()
                .getEntity())));
          });
      }
    };
  }

  public <T> Handler<AsyncResult<Tx<T>>> handleNoContentResponse(Handler<AsyncResult<Response>> asyncResultHandler, Tx<T> tx) {
    return result -> {
      if (result.failed()) {
        HttpException cause = (HttpException) result.cause();
        // The result of rollback operation is not so important, main failure cause is used to build the response
        tx.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
      } else {
        asyncResultHandler.handle(buildNoContentResponse());
      }
    };
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }
}
