package org.folio.services.migration;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.ClientHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public
class UpgradeTenantTest {
    private static final Logger log = LogManager.getLogger(UpgradeTenantTest.class);
    private int okapiPort;
    private Vertx vertx;
    private WebClient webClient;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        webClient = WebClient.create(vertx);
    }

    /**
     * Deploy our Okapi mock on a random free port. Save that port to okapiPort.
     *
     * <p>It also mocks an nginx that translates /okapi/foo/bar to /foo/bar
     */
    Future<Void> deployOkapiMock() {
        return vertx.createHttpServer()
                .requestHandler(httpServerRequest -> {
                    String method = httpServerRequest.method().name();
                    String path = httpServerRequest.path();
                    log.debug("method {} path {}", method, path);
                    if (path.equals("/okapi/finance-storage/funds") && method.equals("GET")) {
                        var json = new JsonObject()
                                .put("totalRecords", 2)
                                .put("funds", new JsonArray()
                                        .add(new JsonObject().put("id", "12345678-51ea-49ad-b8f7-02890dee8711"))
                                        .add(new JsonObject().put("id", "23456789-51ea-49ad-b8f7-02890dee8722")));
                        httpServerRequest.response().setStatusCode(200).end(json.encode());
                        return;
                    }
                    if (path.startsWith("/okapi/orders-storage/configuration/reasons-for-closure/") &&
                            httpServerRequest.method().name().equals("PUT")) {
                        httpServerRequest.response().setStatusCode(201).end("{}");
                        return;
                    }
                    String msg = "Okapi Mock got unexpected " + method + " " + path;
                    log.error(msg);
                    httpServerRequest.response().setStatusCode(404).setStatusMessage(msg).end();
                })
                .listen(0)
                .onSuccess(httpServer -> okapiPort = httpServer.actualPort())
                .mapEmpty();
    }

    HttpRequest<Buffer> withHeaders(HttpRequest<Buffer> httpRequest) {
        httpRequest.putHeader("Content-type", "application/json");
        httpRequest.putHeader("Accept", "application/json,text/plain");
        httpRequest.putHeader("x-okapi-tenant", "diku");
        httpRequest.putHeader("X-Okapi-Url", "http://localhost:" + okapiPort + "/okapi");
        return httpRequest;
    }

    String pojo2json(Object entity) {
        try {
            return ClientHelpers.pojo2json(entity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * call POST at mod-orders-storage /_/tenant
     */
    Future<Void> postTenant(TenantAttributes tenantAttributes) {
        var post = webClient.requestAbs(HttpMethod.POST, "http://localhost:8081/_/tenant");
        return withHeaders(post).sendBuffer(Buffer.buffer(pojo2json(tenantAttributes)))
                .compose(httpResponse -> {
                    if (httpResponse.statusCode() != 201) {
                        return Future.failedFuture(new AssertionError("status code " + httpResponse.statusCode()));
                    }
                    var id = httpResponse.bodyAsJsonObject().getString("id");
                    var get = webClient.requestAbs(HttpMethod.GET, "http://localhost:8081/_/tenant/" + id + "?wait=60000");
                    return withHeaders(get).send();
                }).map(httpResponse -> {
                    if (httpResponse.statusCode() != 200) {
                        throw new AssertionError("status code " + httpResponse.statusCode());
                    }
                    var jsonObject = httpResponse.bodyAsJsonObject();
                    if (! jsonObject.getBoolean("complete")) {
                        throw new AssertionError("not complete: " + jsonObject.encodePrettily());
                    }
                    var messages = jsonObject.getJsonArray("messages");
                    if (! messages.isEmpty()) {
                        throw new AssertionError("errors: " + messages.encodePrettily());
                    }
                    return null;
                });
    }

    Future<Void> initTenant() {
        var tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleTo("mod-orders-storage-99.99.99");
        return postTenant(tenantAttributes);
    }

    Future<Void> upgradeTenant() {
        var tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleFrom("mod-orders-storage-0.0.0");
        tenantAttributes.setModuleTo("mod-orders-storage-99.99.99");
        return postTenant(tenantAttributes);
    }

    @Test
    void test(VertxTestContext vtc) {
        vertx.deployVerticle(new RestVerticle())  // start at default port 8081
        .compose(x -> deployOkapiMock())
        .compose(x -> initTenant())
        .compose(x -> upgradeTenant())
        .onComplete(vtc.succeedingThenComplete());
    }
}
