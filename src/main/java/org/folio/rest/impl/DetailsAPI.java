package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Detail;
import org.folio.rest.jaxrs.model.DetailsCollection;
import org.folio.rest.jaxrs.resource.DetailsResource;
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

public class DetailsAPI implements DetailsResource {
    private static final String DETAIL_TABLE = "details";
    private static final String DETAIL_LOCATION_PREFIX = "/details/";

    private static final Logger log = LoggerFactory.getLogger(DetailsAPI.class);
    private final Messages messages = Messages.getInstance();
    private String idFieldName = "id";

    private static void respond(Handler<AsyncResult<Response>> handler, Response response) {
        AsyncResult<Response> result = Future.succeededFuture(response);
        handler.handle(result);
    }

    private boolean isInvalidUUID (String errorMessage) {
        return (errorMessage != null && errorMessage.contains("invalid input syntax for uuid"));
    }

    public DetailsAPI(Vertx vertx, String tenantId) {
        PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
    }

    @Override
    public void getDetails(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext((Void v) -> {
            try {
                String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

                String[] fieldList = {"*"};
                CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", DETAIL_TABLE));
                CQLWrapper cql = new CQLWrapper(cql2PgJSON, query)
                        .setLimit(new Limit(limit))
                        .setOffset(new Offset(offset));

                PostgresClient.getInstance(vertxContext.owner(), tenantId).get(DETAIL_TABLE, Detail.class, fieldList, cql, true, false, reply -> {
                    try {
                        if (reply.succeeded()) {
                            DetailsCollection collection = new DetailsCollection();
                            @SuppressWarnings("unchecked")
                            List<Detail> results = (List<Detail>)reply.result().getResults();
                            collection.setDetails(results);
                            Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
                            collection.setTotalRecords(totalRecords);
                            Integer first = 0, last = 0;
                            if (results.size() > 0) {
                                first = offset + 1;
                                last = offset + results.size();
                            }
                            collection.setFirst(first);
                            collection.setLast(last);
                            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DetailsResource.GetDetailsResponse
                                    .withJsonOK(collection)));
                        }
                        else {
                            log.error(reply.cause().getMessage(), reply.cause());
                            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DetailsResource.GetDetailsResponse
                                    .withPlainBadRequest(reply.cause().getMessage())));
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DetailsResource.GetDetailsResponse
                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                    }
                });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                String message = messages.getMessage(lang, MessageConsts.InternalServerError);
                if(e.getCause() != null && e.getCause().getClass().getSimpleName().endsWith("CQLParseException")){
                    message = " CQL parse error " + e.getLocalizedMessage();
                }
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(DetailsResource.GetDetailsResponse
                        .withPlainInternalServerError(message)));
            }
        });
    }

    @Override
    public void postDetails(String lang, Detail entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
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
                        DETAIL_TABLE, id, entity,
                        reply -> {
                            try {
                                if (reply.succeeded()) {
                                    String persistenceId = reply.result();
                                    entity.setId(persistenceId);
                                    OutStream stream = new OutStream();
                                    stream.setData(entity);

                                    Response response = DetailsResource.PostDetailsResponse.
                                            withJsonCreated(DETAIL_LOCATION_PREFIX + persistenceId, stream);
                                    respond(asyncResultHandler, response);
                                }
                                else {
                                    log.error(reply.cause().getMessage(), reply.cause());
                                    Response response = DetailsResource.PostDetailsResponse.withPlainInternalServerError(reply.cause().getMessage());
                                    respond(asyncResultHandler, response);
                                }
                            }
                            catch (Exception e) {
                                log.error(e.getMessage(), e);

                                Response response = DetailsResource.PostDetailsResponse.withPlainInternalServerError(e.getMessage());
                                respond(asyncResultHandler, response);
                            }

                        }
                );
            }
            catch (Exception e) {
                log.error(e.getMessage(), e);

                String errMsg = messages.getMessage(lang, MessageConsts.InternalServerError);
                Response response = DetailsResource.PostDetailsResponse.withPlainInternalServerError(errMsg);
                respond(asyncResultHandler, response);
            }

        });
    }

    @Override
    public void getDetailsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext(v -> {
            try {
                String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );

                String idArgument = String.format("'%s'", id);
                Criterion c = new Criterion(
                        new Criteria().addField(idFieldName).setJSONB(false).setOperation("=").setValue(idArgument));

                PostgresClient.getInstance(vertxContext.owner(), tenantId).get(DETAIL_TABLE, Detail.class, c, true,
                        reply -> {
                            try {
                                if (reply.succeeded()) {
                                    @SuppressWarnings("unchecked")
                                    List<Detail> results = (List<Detail>) reply.result().getResults();
                                    if(results.isEmpty()){
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetDetailsByIdResponse
                                                .withPlainNotFound(id)));
                                    }
                                    else{
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetDetailsByIdResponse
                                                .withJsonOK(results.get(0))));
                                    }
                                }
                                else {
                                    log.error(reply.cause().getMessage(), reply.cause());
                                    if(isInvalidUUID(reply.cause().getMessage())){
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetDetailsByIdResponse
                                                .withPlainNotFound(id)));
                                    }
                                    else{
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetDetailsByIdResponse
                                                .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                                    }
                                }
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetDetailsByIdResponse
                                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                            }
                        });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetDetailsByIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
        });
    }

    @Override
    public void deleteDetailsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        String tenantId = TenantTool.tenantId(okapiHeaders);

        try {
            vertxContext.runOnContext(v -> {
                PostgresClient postgresClient = PostgresClient.getInstance(
                        vertxContext.owner(), TenantTool.calculateTenantId(tenantId));

                try {
                    postgresClient.delete(DETAIL_TABLE, id, reply -> {
                        if (reply.succeeded()) {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    DetailsResource.DeleteDetailsByIdResponse.noContent()
                                            .build()));
                        } else {
                            asyncResultHandler.handle(Future.succeededFuture(
                                    DetailsResource.DeleteDetailsByIdResponse.
                                            withPlainInternalServerError(reply.cause().getMessage())));
                        }
                    });
                } catch (Exception e) {
                    asyncResultHandler.handle(Future.succeededFuture(
                            DetailsResource.DeleteDetailsByIdResponse.
                                    withPlainInternalServerError(e.getMessage())));
                }
            });
        }
        catch(Exception e) {
            asyncResultHandler.handle(Future.succeededFuture(
                    DetailsResource.DeleteDetailsByIdResponse.
                            withPlainInternalServerError(e.getMessage())));
        }
    }

    @Override
    public void putDetailsById(String id, String lang, Detail entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
        vertxContext.runOnContext(v -> {
            String tenantId = TenantTool.calculateTenantId( okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT) );
            try {
                if(entity.getId() == null){
                    entity.setId(id);
                }
                PostgresClient.getInstance(vertxContext.owner(), tenantId).update(
                        DETAIL_TABLE, entity, id,
                        reply -> {
                            try {
                                if (reply.succeeded()) {
                                    if (reply.result().getUpdated() == 0) {
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutDetailsByIdResponse
                                                .withPlainNotFound(messages.getMessage(lang, MessageConsts.NoRecordsUpdated))));
                                    }
                                    else{
                                        asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutDetailsByIdResponse
                                                .withNoContent()));
                                    }
                                }
                                else{
                                    log.error(reply.cause().getMessage());
                                    asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutDetailsByIdResponse
                                            .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                                }
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutDetailsByIdResponse
                                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
                            }
                        });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(PutDetailsByIdResponse
                        .withPlainInternalServerError(messages.getMessage(lang, MessageConsts.InternalServerError))));
            }
        });
    }
}
