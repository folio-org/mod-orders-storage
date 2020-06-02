package org.folio.rest.persist;

import static org.folio.rest.persist.PgUtil.response;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HelperUtils {
  private static final Logger log = LoggerFactory.getLogger(HelperUtils.class);

  private static final String POL_NUMBER_PREFIX = "polNumber_";
  private static final String QUOTES_SYMBOL = "\"";
  private static final Pattern orderBy = Pattern.compile("(?<=ORDER BY).*?(?=$|DESC.*$|LIMIT.*$|OFFSET.*$)");

  public static final String JSONB = "jsonb";
  public static final String METADATA = "metadata";
  public static final String ID_FIELD_NAME = "id";

  private HelperUtils() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static <T, E> void getEntitiesCollectionWithDistinctOn(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, QueryHolder queryHolder, String sortField, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    Method respond500 = getRespond500(entitiesMetadataHolder, asyncResultHandler);
    Method respond400 = getRespond400(entitiesMetadataHolder, asyncResultHandler);
    try {
      Matcher matcher = orderBy.matcher(queryHolder.buildCQLQuery().toString());
      String inLowerUnaccentSortField = wrapInLowerUnaccent(String.format("%s->>'%s'", queryHolder.getSearchField(), sortField));
      String distinctOn = matcher.find() ? matcher.group(0) + ", " + inLowerUnaccentSortField : inLowerUnaccentSortField;
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

  public enum SequenceQuery {

    CREATE_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "CREATE SEQUENCE IF NOT EXISTS " + constructSequenceName(purchaseOrderId) + " MINVALUE 1 MAXVALUE 999";
      }
    },
    GET_POL_NUMBER_FROM_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "SELECT * FROM NEXTVAL('" + constructSequenceName(purchaseOrderId) + "')";
      }
    },
    DROP_SEQUENCE {
      @Override
      public String getQuery(String purchaseOrderId) {
        return "DROP SEQUENCE IF EXISTS " + constructSequenceName(purchaseOrderId);
      }
    };

    private static String constructSequenceName(String purchaseOrderId) {
      return QUOTES_SYMBOL + POL_NUMBER_PREFIX + purchaseOrderId + QUOTES_SYMBOL;
    }

    public abstract String getQuery(String purchaseOrderId);
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

}
