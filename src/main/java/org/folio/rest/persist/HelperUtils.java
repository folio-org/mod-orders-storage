package org.folio.rest.persist;

import static org.folio.rest.persist.PgUtil.response;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class HelperUtils {
  private static final Logger log = LogManager.getLogger(HelperUtils.class);

  private static final Pattern orderBy = Pattern.compile("(?<=ORDER BY).*?(?=$|LIMIT.*$|OFFSET.*$)");

  public static final String JSONB = "jsonb";
  public static final String METADATA = "metadata";
  public static final String ID_FIELD_NAME = "id";

  private HelperUtils() {
  }

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
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond400, respond500));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
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
        asyncResultHandler.handle(response(reply.cause().getLocalizedMessage(), respond400, respond500));
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  private static Method getRespond500(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond500WithTextPlainMethod();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  private static Method getRespond400(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond400WithTextPlainMethod();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
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

  public static JsonObject verifyAndExtractBody(org.folio.rest.tools.client.Response response) {
    verifyResponse(response);
    return response.getBody();
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

}
