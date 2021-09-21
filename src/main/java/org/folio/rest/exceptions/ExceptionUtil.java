package org.folio.rest.exceptions;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.exceptions.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.rest.exceptions.ErrorCodes.POSTGRE_SQL_ERROR;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.persist.PgExceptionUtil;

import io.vertx.core.json.JsonObject;

public class ExceptionUtil {
  private static final String ERROR_CAUSE = "cause";

  private ExceptionUtil() {
  }

  public static Errors convertToErrors(Throwable throwable) {
    final Throwable cause = Optional.ofNullable(throwable.getCause()).orElse(throwable);
    Errors errors;
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      errors = convertPgExceptions(badRequestMessage, POSTGRE_SQL_ERROR);
    } else if (cause instanceof io.vertx.ext.web.handler.HttpException) {
      errors = convertVertxHttpException((io.vertx.ext.web.handler.HttpException) cause);
    } else if (cause instanceof HttpException) {
      errors = ((HttpException) cause).getErrors();
      List<Error> errorList = errors.getErrors().stream().map(ExceptionUtil::mapToError).collect(toList());
      errors.setErrors(errorList);
    } else {
      errors = new Errors().withErrors(Collections.singletonList(GENERIC_ERROR_CODE.toError()
                           .withAdditionalProperty(ERROR_CAUSE, cause.getMessage())))
                           .withTotalRecords(1);
    }
    return errors;
  }

  public static boolean isErrorMessageJson(String errorMessage) {
    if (!StringUtils.isEmpty(errorMessage)) {
      Pattern pattern = Pattern.compile("(message).*(code).*(parameters)");
      Matcher matcher = pattern.matcher(errorMessage);
      if (matcher.find()) {
        return matcher.groupCount() == 3;
      }
    }
    return false;
  }

  public static String errorAsString(Errors errors) {
    return Optional.ofNullable(JsonObject.mapFrom(errors).encode()).orElse(ErrorCodes.GENERIC_ERROR_CODE.getDescription());
  }

  public static String errorAsString(Error error) {
    return Optional.ofNullable(JsonObject.mapFrom(error).encode()).orElse(ErrorCodes.GENERIC_ERROR_CODE.getDescription());
  }

  private static Error mapToError(Error error) {
    if (isErrorMessageJson(error.getMessage())) {
      return new JsonObject(error.getMessage()).mapTo(Error.class);
    }
    return error;
  }

  private static Errors convertVertxHttpException(io.vertx.ext.web.handler.HttpException throwable) {
    Errors errors;
    int code = throwable.getStatusCode();
    String message =  throwable.getPayload();
    Error error = new Error().withCode(String.valueOf(code)).withMessage(message);
    errors = new Errors().withErrors(Collections.singletonList(error)).withTotalRecords(1);
    return errors;
  }

  private static Errors convertPgExceptions(String badRequestMessage, ErrorCodes postgreSqlError) {
    List<Error> errorList =  Collections.singletonList(postgreSqlError.toError().withAdditionalProperty(ERROR_CAUSE, badRequestMessage));
    return new Errors().withErrors(errorList).withTotalRecords(1);
  }

}
