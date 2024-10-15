package org.folio.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import one.util.streamex.StreamEx;
import org.folio.okapi.common.GenericCompositeFuture;

public class HelperUtils {
  public static final String ID = "id";

  private HelperUtils() {
  }

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
}
