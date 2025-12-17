package org.folio.rest.persist;

import java.util.Map;

import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import javax.ws.rs.core.Response;

import static org.folio.rest.core.ResponseUtil.httpHandleFailure;

public class DBClient {

  private PostgresClient pgClient;
  private AsyncResult<SQLConnection> sqlConnection;
  private String tenantId;
  private Vertx vertx;


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

  public DBClient(Context context, Map<String, String> headers, PostgresClient pgClient) {
    this.pgClient = pgClient;
    this.vertx = context.owner();
    this.tenantId = TenantTool.tenantId(headers);
  }

  public DBClient(RequestContext requestContext) {
    this(requestContext.getContext(), requestContext.getHeaders());
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }

  public AsyncResult<SQLConnection> getConnection() {
    return sqlConnection;
  }

  public void setConnection(AsyncResult<SQLConnection> sqlConnection) {
    this.sqlConnection = sqlConnection;
  }

  public DBClient withConnection(AsyncResult<SQLConnection> sqlConnection) {
    this.sqlConnection = sqlConnection;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Vertx getVertx() {
    return vertx;
  }
}
