package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Eresource;
import org.folio.rest.jaxrs.model.EresourceCollection;
import org.folio.rest.jaxrs.resource.EresourceResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.TenantTool;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EresourceAPI implements EresourceResource {
    private static final String ERESOURCE_TABLE = "eresource";
    private static final String ERESOURCE_LOCATION_PREFIX = "/eresource/";

    private static final Logger log = LoggerFactory.getLogger(EresourceAPI.class);
    private final Messages messages = Messages.getInstance();
    private String idFieldName = "id";

    private static void respond(Handler<AsyncResult<Response>> handler, Response response) {
        AsyncResult<Response> result = Future.succeededFuture(response);
        handler.handle(result);
    }

    private boolean isInvalidUUID (String errorMessage) {
        return (errorMessage != null && errorMessage.contains("invalid input syntax for uuid"));
    }

    public EresourceAPI(Vertx vertx, String tenantId) { PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName); }

    @Override
    public void getEresource(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext((Void v) -> {
            try {
                String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

                String[] fieldList = {"*"};
                CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", ERESOURCE_TABLE));
                CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
                        .setLimit(new Limit(limit))
                        .setOffset(new Offset(offset));

                PostgresClient.getInstance(vertxContext.owner(), tenantId).get(ERESOURCE_TABLE, Eresource.class, fieldList, cql, true, false, reply -> {
                    try {
                        if (reply.succeeded()) {
                            EresourceCollection collection = new EresourceCollection();
                            @SuppressWarnings("unchecked")
                            List<Eresource> results = (List<Eresource>)reply.result().getResults();
                            collection.setEresources(results);
                            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
                            collection.setTotalRecords(totalRecords);
                            Integer first = 0, last = 0;
                            if (results.size() > 0) {
                                first = offset + 1;
                                last = offset + results.size();
                            }
                            collection.setFirst(first);
                            collection.setLast(last);
                            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(EresourceResource.GetEresourceResponse
                                    .withJsonOK(collection)));
                        }
                        else {
                            log.error(reply.cause().getMessage(), reply.cause());
                            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(EresourceResource.GetEresourceResponse
                                    .withPlainBadRequest(reply.cause().getMessage())));
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(EresourceResource.GetEresourceResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                String message = messages.getMessage(lang, MessageConsts.InternalServerError);
                if(e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")){
                    message = " CQL parse error " + e.getLocalizedMessage();
                }
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(EresourceResource.GetEresourceResponse
                        .withPlainInternalServerError(message)));
            }
        });
    }

    @Override
    public void postEresource(String lang, Eresource entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext(v -> {

            try {
                String id = UUID.randomUUID().toString();
                if(entity.getId() == null){
                    entity.setId(id);
                }
                else{
                    id = entity.getId();
                }

                String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
                PostgresClient.getInstance(vertxContext.owner(), tenantId).save(
                        ERESOURCE_TABLE, id, entity,
                        reply -> {
                            try {
                                if (reply.succeeded()) {
                                    String persistenceId = reply.result();
                                    entity.setId(persistenceId);
                                    OutStream stream = new OutStream();
                                    stream.setData(entity);

                                    Response response = EresourceResource.PostEresourceResponse.
                                            withJsonCreated(ERESOURCE_LOCATION_PREFIX + persistenceId, stream);
                                    respond(asyncResultHandler, response);
                                }
                                else {
                                    log.error(reply.cause().getMessage(), reply.cause());
                                    Response response = EresourceResource.PostEresourceResponse.withPlainInternalServerError(reply.cause().getMessage());
                                    respond(asyncResultHandler, response);
                                }
                            }
                            catch (Exception e) {
                                log.error(e.getMessage(), e);

                                Response response = EresourceResource.PostEresourceResponse.withPlainInternalServerError(e.getMessage());
                                respond(asyncResultHandler, response);
                            }

                        }
                );
            }
            catch (Exception e) {
                log.error(e.getMessage(), e);

                String errMsg = messages.getMessage(lang, MessageConsts.InternalServerError);
                Response response = EresourceResource.PostEresourceResponse.withPlainInternalServerError(errMsg);
                respond(asyncResultHandler, response);
            }

        });
    }

    @Override
    public void getEresourceById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext(v -> {
            try {
                String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

                String idArgument = String.format("'%s'", id);
                Criterion c = new Criterion(
                        new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue(idArgument));

                PostgresClient.getInstance(vertxContext.owner(), tenantId).get(ERESOURCE_TABLE, Eresource.class, c, true,
                        reply -> {
                            try {
                                if (reply.succeeded()) {
                                    @SuppressWarnings("unchecked")
                                    List<Eresource> results = (List<Eresource>) reply.result().getResults();
                                    if(results.isEmpty()){
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetEresourceByIdResponse
                                                .withPlainNotFound(id)));
                                    }
                                    else{
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetEresourceByIdResponse
                                                .withJsonOK(results.get(0))));
                                    }
                                }
                                else {
                                    log.error(reply.cause().getMessage(), reply.cause());
                                    if(isInvalidUUID(reply.cause().getMessage())){
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetEresourceByIdResponse
                                                .withPlainNotFound(id)));
                                    }
                                    else{
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetEresourceByIdResponse
                                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                                    }
                                }
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetEresourceByIdResponse
                                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                            }
                        });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetEresourceByIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
        });
    }

    @Override
    public void deleteEresourceById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        try {
            vertxContext.runOnContext(v -> {
                PostgresClient postgresClient = PostgresClient.getInstance(
                        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

                try {
                    postgresClient.delete(ERESOURCE_TABLE, id, reply -> {
                        if (reply.succeeded()) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    EresourceResource.DeleteEresourceByIdResponse.noContent()
                                            .build()));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    EresourceResource.DeleteEresourceByIdResponse.
                                            withPlainInternalServerError(reply.cause().getMessage())));
                        }
                    });
                } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                            EresourceResource.DeleteEresourceByIdResponse.
                                    withPlainInternalServerError(e.getMessage())));
                }
            });
        }
        catch(Exception e) {
            asyncResultHandler.handle(Future.succeededFuture(
                    EresourceResource.DeleteEresourceByIdResponse.
                            withPlainInternalServerError(e.getMessage())));
        }
    }

    @Override
    public void putEresourceById(String id, String lang, Eresource entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext(v -> {
            String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
            try {
                if(entity.getId() == null){
                    entity.setId(id);
                }
                PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
                        ERESOURCE_TABLE, entity, id,
                        reply -> {
                            try {
                                if (reply.succeeded()) {
                                    if (reply.result().getUpdated() == 0) {
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutEresourceByIdResponse
                                                .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                                    }
                                    else{
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutEresourceByIdResponse
                                                .withNoContent()));
                                    }
                                }
                                else{
                                    log.error(reply.cause().getMessage());
                                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutEresourceByIdResponse
                                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                                }
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutEresourceByIdResponse
                                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                            }
                        });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutEresourceByIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
        });
    }
}
