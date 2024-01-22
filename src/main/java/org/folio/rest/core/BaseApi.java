package org.folio.rest.core;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import io.vertx.core.Context;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import java.net.URI;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.persist.PgExceptionUtil;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.folio.validate.CustomFieldValidationException;
import org.folio.validate.ValidationServiceImpl;

public abstract class BaseApi {
  public Future<Response> buildResponseWithLocation(Object body, String endpoint) {
    return Future.succeededFuture(Response.created(URI.create(endpoint))
      .header(CONTENT_TYPE, APPLICATION_JSON).entity(body).build());
  }

  public Future<Response> buildNoContentResponse() {
    return Future.succeededFuture(Response.noContent().build());
  }

  public Future<Response> buildOkResponse(Object body) {
    return Future.succeededFuture(Response.ok(body, APPLICATION_JSON).build());
  }

  public Future<Response> buildErrorResponse(Throwable throwable) {
    if (throwable instanceof HttpException httpException) {
      return buildErrorResponse(httpException.getStatusCode(), httpException.getPayload());
    }
    if (throwable instanceof CustomFieldValidationException customFieldValidationException) {
      return buildErrorResponse(
          422, Json.encode(customFieldValidationException.getErrors()), APPLICATION_JSON);
    }
    if (StringUtils.isNotBlank(PgExceptionUtil.badRequestMessage(throwable))) {
      return buildErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(), PgExceptionUtil.badRequestMessage(throwable));
    }
    return buildErrorResponse(INTERNAL_SERVER_ERROR.getStatusCode(), throwable.getMessage());
  }

  private Future<Response> buildErrorResponse(int code, String message) {
    return buildErrorResponse(code, message, TEXT_PLAIN);
  }

  private Future<Response> buildErrorResponse(int code, String message, String contentType) {
    return Future.succeededFuture(Response.status(code)
      .header(CONTENT_TYPE, contentType)
      .entity(message)
      .build());
  }

  private Map<String, Object> getCustomFields(Object entity) {
    return JsonObject.mapFrom(entity)
      .getJsonObject("customFields", new JsonObject())
      .getMap()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public Future<Void> validateCustomFields(
    Context vertxContext, Map<String, String> okapiHeaders, Object entity) {
    Map<String, Object> customFieldsMap = getCustomFields(entity);
    if (!customFieldsMap.isEmpty()) {
      return new ValidationServiceImpl(vertxContext)
        .validateCustomFields(customFieldsMap, tenantId(okapiHeaders));
    }
    return Future.succeededFuture();
  }

  protected abstract String getEndpoint(Object entity);
}
