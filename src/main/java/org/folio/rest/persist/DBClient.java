package org.folio.rest.persist;

import java.util.Map;

import org.folio.rest.core.models.RequestContext;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import lombok.Getter;

@Getter
public class DBClient {

  private final PostgresClient pgClient;
  private final String tenantId;
  private final Vertx vertx;

  public DBClient(Context context, Map<String, String> headers) {
    this.pgClient = PgUtil.postgresClient(context, headers);
    this.vertx = context.owner();
    this.tenantId = TenantTool.tenantId(headers);
  }

  public DBClient(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
    this.vertx = vertx;
    this.tenantId = tenantId;
  }

  public DBClient(RequestContext requestContext) {
    this(requestContext.getContext(), requestContext.getHeaders());
  }

}
