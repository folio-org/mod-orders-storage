package org.folio.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import lombok.experimental.UtilityClass;
import one.util.streamex.StreamEx;
import org.folio.okapi.common.GenericCompositeFuture;

@UtilityClass
public class HelperUtils {

  public static final String ID = "id";

  /**
   * Wait for all requests completion and collect all resulting objects. In case any failed, complete resulting future with the exception
   *
   * @param futures list of futures and each produces resulting object on completion
   * @param <T>     resulting type
   * @return resulting objects
   */
  public static <T> Future<List<T>> collectResultsOnSuccess(Collection<Future<T>> futures) {
    return GenericCompositeFuture.join(new ArrayList<>(futures))
      .map(CompositeFuture::list);
  }

  public static String convertIdsToCqlQuery(Collection<String> ids) {
    return convertFieldListToCqlQuery(ids, ID, true);
  }

  public static String convertFieldListToCqlQuery(Collection<String> values, String fieldName, boolean strictMatch) {
    String prefix = fieldName + (strictMatch ? "==(" : "=(");
    return StreamEx.of(values).joining(" or ", prefix, ")");
  }

  public static <T, R> List<R> extractEntityFields(List<T> entities, Function<T, R> fieldExtractor) {
    return entities.stream().map(fieldExtractor).collect(Collectors.toList());
  }

  public static Future<Void> asFuture(Runnable runnable) {
    return asFuture(() -> {
      runnable.run();
      return null;
    });
  }

  public static <T> Future<T> asFuture(Callable<T> callable) {
    try {
      return Future.succeededFuture(callable.call());
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

}
