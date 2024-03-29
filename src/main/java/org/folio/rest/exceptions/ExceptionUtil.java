package org.folio.rest.exceptions;

import static org.folio.rest.exceptions.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.rest.exceptions.ErrorCodes.POSTGRE_SQL_ERROR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.persist.PgExceptionUtil;

import io.vertx.core.json.JsonObject;

public class ExceptionUtil {
  private static final String ERROR_CAUSE = "cause";
  private static final String SQL_STATE = "sqlstate";
  private static final String DETAIL = "detail";
  private static final String MESSAGE = "message";
  public static final String NOT_PROVIDED = "Not Provided";

  private ExceptionUtil() {
  }

  public static Errors convertToErrors(Throwable throwable) {
    final Throwable cause = Optional.ofNullable(throwable.getCause()).orElse(throwable);
    Errors errors;
    Map<Character,String> pgFields = PgExceptionUtil.getBadRequestFields(throwable);
    if (!MapUtils.isEmpty(pgFields)) {
      errors = convertPgExceptions(pgFields);
    } else if (cause instanceof io.vertx.ext.web.handler.HttpException vertxHttpException) {
      errors = convertVertxHttpException(vertxHttpException);
    } else if (cause instanceof HttpException httpException) {
      errors = httpException.getErrors();
      List<Error> errorList = errors.getErrors().stream().map(ExceptionUtil::mapToError).toList();
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
    String message =  Optional.ofNullable(throwable.getPayload()).orElse(throwable.getMessage());
    Error error = new Error().withCode(String.valueOf(code)).withMessage(message);
    errors = new Errors().withErrors(Collections.singletonList(error)).withTotalRecords(1);
    return errors;
  }

  private static Errors convertPgExceptions( Map<Character,String> pgFields) {
    List<Parameter> parameters = new ArrayList<>();
    if (!MapUtils.isEmpty(pgFields)) {
      String sqlstate = pgFields.getOrDefault('C', NOT_PROVIDED);
      String detail = pgFields.getOrDefault('D', NOT_PROVIDED);
      String message = pgFields.getOrDefault('M', NOT_PROVIDED);
      parameters.add(new Parameter().withKey(SQL_STATE).withValue(sqlstate));
      parameters.add(new Parameter().withKey(DETAIL).withValue(detail));
      parameters.add(new Parameter().withKey(MESSAGE).withValue(message));
    } else {
      parameters.add(new Parameter().withKey(SQL_STATE).withValue(POSTGRE_SQL_ERROR.getCode()));
    }
    List<Error> errorList =  Collections.singletonList(POSTGRE_SQL_ERROR.toError().withParameters(parameters));
    return new Errors().withErrors(errorList).withTotalRecords(1);
  }

}
