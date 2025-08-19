package org.folio.rest.persist;

import static org.folio.rest.exceptions.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.rest.persist.PgUtil.response;
import static org.folio.rest.exceptions.ErrorCodes.UNIQUE_FIELD_CONSTRAINT_ERROR;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import lombok.experimental.UtilityClass;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

@UtilityClass
public class HelperUtils {
  private static final Logger log = LogManager.getLogger();
  private static final Pattern orderBy = Pattern.compile("(?<=ORDER BY).*?(?=$|LIMIT.*$|OFFSET.*$)");

  public static final String JSONB = "jsonb";
  public static final String METADATA = "metadata";
  public static final String ID_FIELD_NAME = "id";

  public static <T, E> void getEntitiesCollectionWithDistinctOn(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, QueryHolder queryHolder, String sortField, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    Method respond500 = getRespond500(entitiesMetadataHolder, asyncResultHandler);
    Method respond400 = getRespond400(entitiesMetadataHolder, asyncResultHandler);
    try {
      Matcher matcher = orderBy.matcher(queryHolder.buildCQLQuery().toString());
      String orderByFields = matcher.find() ? matcher.group(0).replaceAll(" DESC\\b", "") + ", " : "";  // \b = word boundary

      String inLowerUnaccentSortField = wrapInLowerUnaccent(String.format("%s->>'%s'", queryHolder.getSearchField(), sortField));
      String distinctOn = orderByFields + inLowerUnaccentSortField;

      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
      postgresClient.get(queryHolder.getTable(), entitiesMetadataHolder.getClazz(), JSONB, queryHolder.buildCQLQuery(), true, false, null, distinctOn,
        reply -> processDbReply(entitiesMetadataHolder, asyncResultHandler, respond500, respond400, reply));
    } catch (CQLQueryValidationException e) {
      log.error("CQLQueryValidationException in getEntitiesCollectionWithDistinctOn", e);
      asyncResultHandler.handle(response(e.getMessage(), respond400, respond500));
    } catch (Exception e) {
      log.error("Error in getEntitiesCollectionWithDistinctOn", e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  public static Criterion getCriterionByFieldNameAndValue(String filedName, String fieldValue) {
    Criteria a = new Criteria();
    a.addField("'" + filedName + "'");
    a.setOperation("=");
    a.setVal(fieldValue);
    return new Criterion(a);
  }

  public static Criterion getCriteriaByFieldNameAndValueNotJsonb(String fieldName, String fieldValue) {
    Criteria a = new Criteria();
    a.addField(fieldName);
    a.setOperation("=");
    a.setVal(fieldValue);
    a.setJSONB(false);
    return new Criterion(a);
  }

  private static <T, E> void processDbReply(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler, Method respond500, Method respond400, AsyncResult<Results<T>> reply) {
    try {
      Method respond200 = entitiesMetadataHolder.getRespond200WithApplicationJson();
      if (reply.succeeded()) {
        E collection = entitiesMetadataHolder.getCollectionClazz().getDeclaredConstructor().newInstance();
        List<T> results = reply.result().getResults();
        Method setResults =  entitiesMetadataHolder.getSetResultsMethod();
        Method setTotalRecordsMethod =  entitiesMetadataHolder.getSetTotalRecordsMethod();
        setResults.invoke(collection, results);
        Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
        setTotalRecordsMethod.invoke(collection, totalRecords);
        asyncResultHandler.handle(response(collection, respond200, respond500));
      } else {
        log.error("processDbReply - request failed", reply.cause());
        asyncResultHandler.handle(response(reply.cause().getLocalizedMessage(), respond400, respond500));
      }
    } catch (Exception e) {
      log.error("Error in processDbReply", e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  private static Method getRespond500(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond500WithTextPlainMethod();
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  private static Method getRespond400(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond400WithTextPlainMethod();
    } catch (Exception e) {
      log.error(e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  public static String getEndpoint(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value();
  }

  /**
   * Return "lower(f_unaccent(" + term + "))".
   *
   * @param term String to wrap
   * @return wrapped term
   */
  private static String wrapInLowerUnaccent(String term) {
    return String.format("lower(f_unaccent(%s))", term);
  }

  public static void verifyResponse(org.folio.rest.tools.client.Response response) {
    if (!org.folio.rest.tools.client.Response.isSuccess(response.getCode())) {
      throw new CompletionException(
        new HttpException(response.getCode(), response.getError().getString("errorMessage")));
    }
  }

  /**
   * @param query string representing CQL query
   * @return URL encoded string
   */
  public static String encodeQuery(String query) {
    try {
      return URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      log.error("Error happened while attempting to encode '{}'", query, e);
      throw new CompletionException(e);
    }
  }

  public static String getSQLUniqueConstraintName(String errorMessage) {
    if (!StringUtils.isEmpty(errorMessage)) {
      Pattern pattern = Pattern.compile("(unique constraint)\\s+\"(?<constraint>.*?)\"");
      Matcher matcher = pattern.matcher(errorMessage);
      if (matcher.find()) {
        return matcher.group("constraint");
      }
    }
    return StringUtils.EMPTY;
  }

  public static Error buildFieldConstraintError(String entityName, String fieldName) {
    final String FIELD_NAME = "field";
    final String ENTITY_NAME = "entity";
    String description = MessageFormat.format(UNIQUE_FIELD_CONSTRAINT_ERROR.getDescription(), fieldName);
    String code = MessageFormat.format(UNIQUE_FIELD_CONSTRAINT_ERROR.getCode(), entityName, fieldName);
    Error error = new Error().withCode(code).withMessage(description);
    error.getParameters().add(new Parameter().withKey(FIELD_NAME).withValue(fieldName));
    error.getParameters().add(new Parameter().withKey(ENTITY_NAME).withValue(entityName));
    return error;
  }

  public static <T> HttpException buildException(AsyncResult<T> reply, Class<?> clazz ) {
    String error = convertExceptionToStringError(reply, clazz);
    if (GENERIC_ERROR_CODE.getCode().equals(error)) {
      return new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), error);
    }
    return new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), error);
  }

  public static <T> String convertExceptionToStringError(AsyncResult<T> reply, Class<?> clazz) {
    String msg = PgExceptionUtil.badRequestMessage(reply.cause());
    return Optional.ofNullable(msg)
      .map(HelperUtils::getSQLUniqueConstraintName)
      .map(uniqueConstraintName -> buildFieldConstraintError(uniqueConstraintName, clazz))
      .orElse(GENERIC_ERROR_CODE.getCode());
  }

  public static String buildFieldConstraintError(String uniqueConstraintName, Class<?> clazz)  {
    final String FIELD_VALUE = "value";
    final String FIELD_CODE = "Code";
    final String FIELD_NAME = "Name";

    String errorField = null;
    if (uniqueConstraintName.contains(FIELD_VALUE)) {
      errorField = FIELD_VALUE;
    } else if (uniqueConstraintName.contains(FIELD_CODE.toLowerCase())) {
      errorField = FIELD_CODE;
    } else if (uniqueConstraintName.contains(FIELD_NAME.toLowerCase())) {
      errorField = FIELD_NAME;
    }

    return errorField != null
      ? JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(clazz.getSimpleName(), errorField)).encode()
      : JsonObject.mapFrom(GENERIC_ERROR_CODE.toError()).encode();
  }

  public static String getFullTableName(String tenantId, String tableName) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + tableName;
  }

  public static String getQueryValues(List<JsonObject> entities) {
    return entities.stream().map(entity -> "('" + entity.getString("id") + "', $$" + entity.encode() + "$$::json)").collect(
      Collectors.joining(","));
  }

}
