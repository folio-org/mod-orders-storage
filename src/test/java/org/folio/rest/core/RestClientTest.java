package org.folio.rest.core;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.util.TestConstants.OKAPI_URL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.UUID;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.rest.core.models.RequestContext;
import org.folio.rest.core.models.RequestEntry;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@ExtendWith(VertxExtension.class)
public class RestClientTest {

  @Mock
  private Context ctxMock;

  private AutoCloseable mockClosable;

  @BeforeEach
  public void initMocks() {
    mockClosable = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void releaseMocks() throws Exception {
    mockClosable.close();
  }

  @Test
  void negative_getByShouldThrowException(Vertx vertx, VertxTestContext testContext) {
    var userId = UUID.randomUUID().toString();
    RequestEntry requestEntry = new RequestEntry("/users/" + userId);
    vertx.createHttpServer()
      .requestHandler(request -> request.response().setStatusCode(400).end())
      .listen(0)
      .compose(httpServer -> new RestClient().get(requestEntry, requestContext(httpServer, "bee", "janetoken")))
      .onComplete(testContext.failingThenComplete());
  }

  @Test
  void positive_getByShouldCreateJson(Vertx vertx, VertxTestContext testContext) {
    var userId = UUID.randomUUID().toString();
    RequestEntry requestEntry = new RequestEntry("/users/" + userId);
    vertx.createHttpServer()
      .requestHandler(request -> testContext.verify(() -> {
        assertThat(request.path(), is("/users/" + userId));
        assertThat(request.getHeader(OKAPI_HEADER_TENANT), is("bee"));
        assertThat(request.getHeader(OKAPI_HEADER_TOKEN), is("janetoken"));
        request.response().end("{ \"username\":\"jane\" }");
      }))
      .listen(0)
      .compose(host -> new RestClient().get(requestEntry, requestContext(host, "bee", "janetoken")))
      .onComplete(testContext.succeeding(jsonObject -> {
        assertThat(jsonObject.getString("username"), is("jane"));
        testContext.completeNow();
      }));
  }

  private RequestContext requestContext(HttpServer httpServer, String tenant, String token) {
    var port = httpServer == null ? NetworkUtils.nextFreePort() : httpServer.actualPort();
    var headers = new CaseInsensitiveMap<>(Map.of(
      OKAPI_URL, "http://localhost:" + port,
      OKAPI_HEADER_TENANT, tenant,
      OKAPI_HEADER_TOKEN, token));
    return new RequestContext(ctxMock, headers);
  }
}
