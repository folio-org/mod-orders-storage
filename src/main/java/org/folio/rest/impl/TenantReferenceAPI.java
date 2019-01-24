package org.folio.rest.impl;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;

import io.vertx.core.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;

public class TenantReferenceAPI extends TenantAPI {

  private static final String JAR_PROTOCOL = "jar";

  private static final String FILE_PROTOCOL = "file";

  private static final String RESOURCES_PATH = "data/";

  private static final String ORDERS_STORAGE_PREFIX_URL = "/orders-storage/";

  private static final String PARAMETER_LOAD_SAMPLE = "loadSample";

  private static final Logger log = LoggerFactory.getLogger(TenantReferenceAPI.class);

  private HttpClient httpClient;


  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");

    httpClient = cntxt.owner().createHttpClient();
    super.postTenant(tenantAttributes, headers, res -> {
      //if a system parameter is passed from command line, ex: loadSample=true that value is considered,
      //Priority of Parameter Tenant Attributes > command line parameter > default (false)
      boolean loadSample = Boolean.parseBoolean(MODULE_SPECIFIC_ARGS.getOrDefault(PARAMETER_LOAD_SAMPLE,
          "false"));
      List<Parameter> parameters = tenantAttributes.getParameters();
      for (Parameter parameter : parameters) {
        if (PARAMETER_LOAD_SAMPLE.equals(parameter.getKey())
          && "true".equals(parameter.getValue())) {
          loadSample = true;
        }
      }
      log.info("postTenant loadSampleData=" + loadSample);
      if (res.succeeded() && loadSample) {
        loadSampleData(headers, hndlr);
      } else {
        hndlr.handle(res);
      }
    }, cntxt);
  }

  private void loadSampleData(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr) {

      try {
        // Get all the folders from data/ directory, and load data for those end points
        List<String> list = getResourceEndPointsfromClassPathDir();

        loadSampleData(headers, list.iterator(), res -> {
          if (res.failed()) {
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond500WithTextPlain(res.cause().getLocalizedMessage())));
          } else {
            hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
              .respond201WithApplicationJson("")));
          }
        });
      } catch (Exception exception) {
        hndlr.handle(io.vertx.core.Future.succeededFuture(PostTenantResponse
            .respond500WithTextPlain(exception.getLocalizedMessage())));
      }

  }

  /**
   * Read the sub directories under "data/" which contains data to be loaded from jar or Path
   *
   * @return
   * @throws URISyntaxException
   * @throws IOException
   */
  private List<String> getResourceEndPointsfromClassPathDir() throws URISyntaxException, IOException {
    URL url = Thread.currentThread().getContextClassLoader().getResource(RESOURCES_PATH);
    List<String> list = new ArrayList<>();
    if (url != null) {
      if (url.getProtocol().equals(FILE_PROTOCOL)) {
        for (File filename : getFilesfromPath(url)) {
          list.add(filename.getName());
        }
      } else if (url.getProtocol().equals(JAR_PROTOCOL)) {
        return getAPINamesFromJar(RESOURCES_PATH,url)
        .stream().filter(filesList-> !filesList.endsWith(".json"))
        .map(directory->directory.substring(5, directory.length() - 1)).collect(toList());
      }
    }
    return list;
  }



  private void loadSampleData(Map<String, String> headers, Iterator<String> iterator, Handler<AsyncResult<Response>> res) {
    if (!iterator.hasNext()) {
      res.handle(Future.succeededFuture());
    } else {
      String endPoint = iterator.next();
      loadSampleDataForEndpoint(headers, endPoint, asyncResult -> {
        if (asyncResult.failed()) {
          res.handle(Future.failedFuture(asyncResult.cause()));
        } else {
          loadSampleData(headers, iterator, res);
        }
      });
    }
  }
  /**
   * For Each Sub-Directory under "data/", load all the files present
   *
   * @param headers
   * @param endPoint
   * @param handler
   */
  private void loadSampleDataForEndpoint(Map<String, String> headers, String endPoint, Handler<AsyncResult<Void>> handler) {
    log.info("load Sample data for: " + endPoint + " begin");
    String okapiUrl = headers.get("X-Okapi-Url-to");
    if (okapiUrl == null) {
      log.warn("No X-Okapi-Url-to. Headers: " + headers);
      handler.handle(Future.failedFuture("No X-Okapi-Url-to header"));
      return;
    }
    log.info("load Sample Data....................");
    List<String> jsonList = new LinkedList<>();
    try {
      List<InputStream> streams = getStreamsfromClassPathDir(RESOURCES_PATH + endPoint);
      for (InputStream stream : streams) {
        jsonList.add(IOUtils.toString(stream, "UTF-8"));
      }
    } catch (URISyntaxException ex) {
      handler.handle(Future.failedFuture("URISyntaxException for path " + endPoint + " ex=" + ex.getLocalizedMessage()));
      return;

    } catch (IOException ex) {
      handler.handle(Future.failedFuture("IOException for path " + endPoint + " ex=" + ex.getLocalizedMessage()));
      return;
    }
    final String endPointUrl = okapiUrl + ORDERS_STORAGE_PREFIX_URL + endPoint;
    List<Future> futures = new LinkedList<>();
    for (String json : jsonList) {
      Future<Void> future = Future.future();
      futures.add(future);

      postData(headers, endPointUrl, json, future);
    }
    CompositeFuture.all(futures).setHandler(asyncResult -> {
      log.info("Sample Data load {} done. success={}", endPoint, asyncResult.succeeded());
      if (asyncResult.failed()) {
        handler.handle(Future.failedFuture(asyncResult.cause().getLocalizedMessage()));
      } else {
        handler.handle(Future.succeededFuture());
      }
    });
  }


  private void postData(Map<String, String> headers, final String endPointUrl, String json, Future<Void> f) {
    HttpClientRequest req = httpClient.postAbs(endPointUrl, responseHandler -> {
      if (responseHandler.statusCode() >= 200 && responseHandler.statusCode() <= 299) {
        f.handle(Future.succeededFuture());
      } else {
        f.handle(Future.failedFuture("POST " + endPointUrl + " returned status " + responseHandler.statusCode()));
      }
    });
    for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
      String header = headerEntry.getKey();
      if (header.startsWith("X-") || header.startsWith("x-")) {
        req.headers().add(header, headerEntry.getValue());
      }
    }
    req.headers().add("Content-Type", "application/json");
    req.headers().add("Accept", "application/json, text/plain");
    req.end(json);
  }

  private List<InputStream> getStreamsfromClassPathDir(String directoryName) throws URISyntaxException, IOException {
    List<InputStream> streams = new LinkedList<>();

    URL url = Thread.currentThread().getContextClassLoader().getResource(directoryName);
    if (url != null) {
      if (url.getProtocol().equals(FILE_PROTOCOL)) {
        for(File file: getFilesfromPath(url)) {
          streams.add(new FileInputStream(file));
        }
      } else if (url.getProtocol().equals(JAR_PROTOCOL)) {
        getAPINamesFromJar(directoryName + "/", url)
        .forEach(n->streams.add(Thread.currentThread().getContextClassLoader().getResourceAsStream(n)));
      }
    }
    return streams;
  }

  private File[] getFilesfromPath(URL url)
      throws URISyntaxException {
    File file = Paths.get(url.toURI()).toFile();
    if (file != null) {
      File[] files = file.listFiles();
      if (files != null) {
        return files;
      }
    }
    return new File[0];
  }

  private List<String> getAPINamesFromJar(String directoryName, URL url)
      throws IOException {
      List<String> fileNames = new ArrayList<>();
      String path = url.getPath();
      String jarPath = path.substring(5, path.indexOf('!'));
      try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name()))) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if(name.startsWith(directoryName) && !directoryName.equals(name)){
            fileNames.add(name);
        }
      }
    }
      return fileNames;
  }


  @Override
  public void getTenant(Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("getTenant");
    super.getTenant(map, hndlr, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> map, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenant(map, hndlr, cntxt);
  }
}