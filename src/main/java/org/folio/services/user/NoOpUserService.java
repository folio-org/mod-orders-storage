package org.folio.services.user;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.Map;
import org.folio.model.User;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.service.UserService;

/**
 * A NoOp implementation for {@link UserService}. It does not initiate any web requests to the users
 * API.
 */
public class NoOpUserService extends UserService {

  public NoOpUserService(Vertx vertx) {
    super(vertx);
  }

  /**
   * Returns a {@link User} object with it's {@code id} set to the value of the {@code
   * X-Okapi-User-Id} header. If no such header is present the returned object will be empty.
   *
   * @param okapiHeaders The headers for the current API call.
   * @return User information based on userid from header.
   */
  @Override
  public Future<User> getUserInfo(final Map<String, String> okapiHeaders) {
    User user = new User();
    user.setId(okapiHeaders.get(XOkapiHeaders.USER_ID));
    return Future.succeededFuture(user);
  }
}
