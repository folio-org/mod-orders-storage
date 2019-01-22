package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TenantSampleDataApi extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantSampleDataApi.class);

  private static final String INSERT_IF_NOT_EXIST = "INSERT INTO %s.%s (id, jsonb) " +
    "VALUES('%s','%s'::jsonb)" +
    "ON CONFLICT (id)" +
    "DO NOTHING;";
  private static final String FILE_PROTOCOL = "file";
  private static final String JAR_PROTOCOL = "jar";

  private Context context;
  private static final List<String> TABLES_NAME_LIST = Collections.unmodifiableList(Arrays.asList(
    "purchase_order", "adjustment", "claim", "cost",
    "details", "eresource", "fund_distribution", "location",
    "physical", "source", "vendor_detail", "po_line"));

  @Override
  public void postTenant(TenantAttributes entity, Map<String, String> headers, Handler<AsyncResult<Response>> handlers, Context context) {
    log.info("postTenant");
    this.context = context;
    super.postTenant(entity, headers, res -> {
      if (res.succeeded()) {
        loadReferenceData(headers, handlers);
      } else {
        handlers.handle(res);
      }
    }, context);
  }

  private List<InputStream> getStreamsFromClassPathDir(String directoryName) throws URISyntaxException, IOException {
    List<InputStream> streams = new LinkedList<>();

    URL url = Thread.currentThread().getContextClassLoader().getResource(directoryName);
    if (url != null) {
      if (url.getProtocol().equals(FILE_PROTOCOL)) {
        streams = getStreamsFromFileSystem(url);
      } else if (url.getProtocol().equals(JAR_PROTOCOL)) {
        streams = getStreamsFromJar(directoryName, url);
      }
    }
    return streams;
  }

  private List<InputStream> getStreamsFromFileSystem(URL url) throws URISyntaxException, FileNotFoundException {
    List<InputStream> streams = new LinkedList<>();
    File file = Paths.get(url.toURI()).toFile();
    if (file != null) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File filename : files) {
          streams.add(new FileInputStream(filename));
        }
      }
    }
    return streams;
  }

  private List<InputStream> getStreamsFromJar(String directoryName, URL url) throws IOException {
    List<InputStream> streams = new LinkedList<>();
    String dirPath = directoryName + File.separator;
    String path = url.getPath();
    String jarPath = getJarFilePath(path);
    try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()))) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (isSampleData(dirPath, name)) {
          streams.add(Thread.currentThread().getContextClassLoader().getResourceAsStream(name));
        }
      }
    }
    return streams;
  }

  private boolean isSampleData(String dirPath, String name) {
    return name.startsWith(dirPath) && !dirPath.equals(name);
  }

  private String getJarFilePath(String path) {
    return path.substring(5, path.indexOf('!'));
  }

  private void loadReferenceData(Map<String, String> headers, Handler<AsyncResult<Response>> handler) {

    loadRef(headers, TABLES_NAME_LIST.iterator(), res -> {
      if (res.failed()) {
        handler.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond500WithTextPlain(res.cause().getLocalizedMessage())));
      } else {
        handler.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
          .respond201WithApplicationJson("")));
      }
    });
  }

  private void loadRef(Map<String, String> headers, Iterator<String> it, Handler<AsyncResult<Response>> handler) {
    if (!it.hasNext()) {
      handler.handle(Future.succeededFuture());
    } else {
      String endPoint = it.next();
      loadRef(headers, endPoint, x -> {
        if (x.failed()) {
          handler.handle(Future.failedFuture(x.cause()));
        } else {
          loadRef(headers, it, handler);
        }
      });
    }
  }

  private void loadRef(Map<String, String> headers, String table, Handler<AsyncResult<Void>> handler) {
    log.info("loadRef {} begin", table);
    List<String> jsonList = new LinkedList<>();
    try {
      List<InputStream> streams = getStreamsFromClassPathDir("data/" + table);
      for (InputStream stream : streams) {
        jsonList.add(IOUtils.toString(stream, "UTF-8"));
      }
    } catch (URISyntaxException ex) {
      handler.handle(Future.failedFuture("URISyntaxException for path " + table + " ex=" + ex.getLocalizedMessage()));
      return;

    } catch (IOException ex) {
      handler.handle(Future.failedFuture("IOException for path " + table + " ex=" + ex.getLocalizedMessage()));
      return;
    }

    List<Future> futures = new LinkedList<>();
    for (String json : jsonList) {
      String tenant =  headers.get("X-Okapi-Tenant");
      Future<Void> future = Future.future();
      PostgresClient.getInstance(context.owner(), tenant)
        .execute(buildInsertIfNotExistQuery(json, tenant, table), updateResult -> handle(future, updateResult));
      futures.add(future);
    }
    CompositeFuture.all(futures).setHandler(asyncResult -> {
      log.info("loadRef {} done. success={}", table, asyncResult.succeeded());
      handle(handler, asyncResult);
    });
  }

  private void handle(Handler<AsyncResult<Void>> asyncResultHandler, AsyncResult<?> asyncResult) {
    if (asyncResult.failed()) {
      asyncResultHandler.handle(Future.failedFuture(asyncResult.cause().getLocalizedMessage()));
    } else {
      asyncResultHandler.handle(Future.succeededFuture());
    }
  }

  private String buildInsertIfNotExistQuery(String json, String tenant, String table) {
    String id = new JsonObject(json).getString("id");
    String schema = PostgresClient.convertToPsqlStandard(tenant);
    return String.format(INSERT_IF_NOT_EXIST, schema, table, id, json);
  }


}
