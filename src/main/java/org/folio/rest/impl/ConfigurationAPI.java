package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Prefix;
import org.folio.rest.jaxrs.model.PrefixCollection;
import org.folio.rest.jaxrs.model.ReasonForClosure;
import org.folio.rest.jaxrs.model.ReasonForClosureCollection;
import org.folio.rest.jaxrs.model.Suffix;
import org.folio.rest.jaxrs.model.SuffixCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageConfiguration;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class ConfigurationAPI implements OrdersStorageConfiguration {

  public static final String REASON_FOR_CLOSURE_TABLE = "reasons_for_closure";
  public static final String PREFIX_TABLE = "prefixes";
  public static final String SUFFIX_TABLE = "suffixes";

  @Override
  @Validate
  public void getOrdersStorageConfigurationReasonsForClosure(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(REASON_FOR_CLOSURE_TABLE, ReasonForClosure.class, ReasonForClosureCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetOrdersStorageConfigurationReasonsForClosureResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageConfigurationReasonsForClosure(ReasonForClosure entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(REASON_FOR_CLOSURE_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageConfigurationReasonsForClosureResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageConfigurationReasonsForClosureById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(REASON_FOR_CLOSURE_TABLE, ReasonForClosure.class, id, okapiHeaders,vertxContext, GetOrdersStorageConfigurationReasonsForClosureByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageConfigurationReasonsForClosureById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(REASON_FOR_CLOSURE_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageConfigurationReasonsForClosureByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageConfigurationReasonsForClosureById(String id, ReasonForClosure entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(REASON_FOR_CLOSURE_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageConfigurationReasonsForClosureByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageConfigurationPrefixes(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PREFIX_TABLE, Prefix.class, PrefixCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetOrdersStorageConfigurationPrefixesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageConfigurationPrefixes(Prefix entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(PREFIX_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageConfigurationPrefixesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageConfigurationPrefixesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PREFIX_TABLE, Prefix.class, id, okapiHeaders,vertxContext, GetOrdersStorageConfigurationPrefixesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageConfigurationPrefixesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PREFIX_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageConfigurationPrefixesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageConfigurationPrefixesById(String id, Prefix entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(PREFIX_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageConfigurationPrefixesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageConfigurationSuffixes(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(SUFFIX_TABLE, Suffix.class, SuffixCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetOrdersStorageConfigurationSuffixesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postOrdersStorageConfigurationSuffixes(Suffix entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(SUFFIX_TABLE, entity, okapiHeaders, vertxContext, PostOrdersStorageConfigurationSuffixesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getOrdersStorageConfigurationSuffixesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(SUFFIX_TABLE, Suffix.class, id, okapiHeaders,vertxContext, GetOrdersStorageConfigurationSuffixesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteOrdersStorageConfigurationSuffixesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SUFFIX_TABLE, id, okapiHeaders, vertxContext, DeleteOrdersStorageConfigurationSuffixesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putOrdersStorageConfigurationSuffixesById(String id, Suffix entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SUFFIX_TABLE, entity, id, okapiHeaders, vertxContext, PutOrdersStorageConfigurationSuffixesByIdResponse.class, asyncResultHandler);
  }
}
