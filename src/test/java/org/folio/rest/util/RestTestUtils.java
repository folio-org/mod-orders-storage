package org.folio.rest.util;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.folio.rest.util.TestConstants.X_OKAPI_TENANT;
import static org.folio.rest.util.TestConstants.X_OKAPI_USER_ID;

import java.util.Map;
import java.util.Objects;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

public class RestTestUtils {

  public static Response verifyPostResponse(String url, Object body, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(TestConfig.X_OKAPI_URL, X_OKAPI_TENANT, TestConstants.X_OKAPI_TOKEN);
    return verifyPostResponse(url, body, headers, expectedContentType, expectedCode);
  }

  public static Response verifyPostResponse(String url, Object body, Headers headers, String expectedContentType, int expectedCode) {
    body = Objects.isNull(body) ? "" : convertToJson(body).encodePrettily();
    return RestAssured
      .with()
        .header(TestConfig.X_OKAPI_URL)
        .headers(headers)
        .header(TestConstants.X_OKAPI_TOKEN)
        .contentType(APPLICATION_JSON)
        .body(body)
      .post(url)
        .then()
          .log().all()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
            .extract().response();
  }

  public static Response verifyPut(String url, Object body, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(TestConfig.X_OKAPI_URL, X_OKAPI_TENANT, TestConstants.X_OKAPI_TOKEN, X_OKAPI_USER_ID);
    return verifyPut(url, convertToJson(body).encodePrettily(), headers, expectedContentType, expectedCode);
  }

  public static Response verifyPut(String url, String body, Headers headers, String expectedContentType, int expectedCode) {
    return RestAssured
      .with()
        .headers(headers)
        .header(TestConfig.X_OKAPI_URL)
        .body(body)
        .contentType(APPLICATION_JSON)
      .put(url)
        .then()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
          .extract()
            .response();
  }

  public static Response verifyGet(String url, String expectedContentType, int expectedCode) {
    Headers headers = prepareHeaders(TestConfig.X_OKAPI_URL, X_OKAPI_TENANT);
    return verifyGet(url, headers, expectedContentType, expectedCode);
  }

  static Response verifyGet(String url, Headers headers, String expectedContentType, int expectedCode) {
    final RequestSpecification specification = RestAssured.with()
      .headers(headers);
    return verifyGet(specification, url, expectedContentType, expectedCode);
  }

  public static Response verifyGetWithParam(String url, String expectedContentType, int expectedCode, String paramName, String paramValue) {
    Headers headers = prepareHeaders(TestConfig.X_OKAPI_URL, X_OKAPI_TENANT);
    RequestSpecification specification = RestAssured.with()
      .headers(headers)
      .queryParam(paramName, paramValue);
    return verifyGet(specification, url, expectedContentType, expectedCode);
  }

  public static Response verifyGetWithParam(String url, String expectedContentType, int expectedCode, Map<String, Object> params) {
    Headers headers = prepareHeaders(TestConfig.X_OKAPI_URL, X_OKAPI_TENANT);
    RequestSpecification specification = RestAssured.with()
      .headers(headers)
      .queryParams(params);
    return verifyGet(specification, url, expectedContentType, expectedCode);
  }

  static Response verifyGet(RequestSpecification requestSpecification, String url, String expectedContentType, int expectedCode) {
    return requestSpecification
      .get(url)
        .then()
          .log().all()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
            .extract()
              .response();
  }

  public static Response verifyDeleteResponse(String url, String expectedContentType, int expectedCode) {
    Headers headers =  prepareHeaders(TestConfig.X_OKAPI_URL, X_OKAPI_TENANT);
    return verifyDeleteResponse(url, headers, expectedContentType, expectedCode);
  }

  static Response verifyDeleteResponse(String url, Headers headers, String expectedContentType, int expectedCode) {
    return RestAssured
      .with()
        .headers(headers)
      .delete(url)
        .then()
          .statusCode(expectedCode)
          .contentType(expectedContentType)
          .extract()
            .response();
  }

  public static Headers prepareHeaders(Header... headers) {
    return new Headers(headers);
  }

  public static String buildQueryParam(String query) {
    return "?query=" + query;
  }

  public static JsonObject convertToJson(Object data) {
    return data instanceof JsonObject ? (JsonObject) data : JsonObject.mapFrom(data);
  }
}
