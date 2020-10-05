package org.folio.rest.util;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;

import io.restassured.http.Header;

public final class TestConstants {

  private TestConstants() {}

  private static final String FINANCE_TENANT = "financeimpltest";
  public static final String ERROR_TENANT = "error_tenant";
  public static final String OKAPI_URL = "X-Okapi-Url";
  public static final String VALID_UUID = "8d3881f6-dd93-46f0-b29d-1c36bdb5c9f9";
  public static final String VALID_OKAPI_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0aW5nX2FkbWluIiwidXNlcl9pZCI6ImQ5ZDk1ODJlLTY2YWQtNWJkMC1iM2NiLTdkYjIwZTc1MzljYyIsImlhdCI6MTU3NDgxMTgzMCwidGVuYW50Ijoic3VwZXJ0ZW5hbnQifQ.wN7g6iBVw1czV2lBdZySQHpX-dKcK35Wc0f3mFvKEOs";
  public static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, FINANCE_TENANT);
  public static final Header EMPTY_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "EmptyConfig");
  public static final Header INVALID_CONFIG_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "InvalidConfig");
  public static final Header ERROR_X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, ERROR_TENANT);
  public static final Header X_OKAPI_TOKEN = new Header(OKAPI_HEADER_TOKEN, VALID_OKAPI_TOKEN);
  public static final String BAD_QUERY = "unprocessableQuery";
  public static final String ID_DOES_NOT_EXIST = "d25498e7-3ae6-45fe-9612-ec99e2700d2f";
  public static final String SERIES_DOES_NOT_EXIST = ID_DOES_NOT_EXIST;
  public static final String ID_FOR_INTERNAL_SERVER_ERROR = "168f8a86-d26c-406e-813f-c7527f241ac3";
  public static final String SERIES_INTERNAL_SERVER_ERROR = ID_FOR_INTERNAL_SERVER_ERROR;
  public static final String BASE_MOCK_DATA_PATH = "mockdata/";
  public static final String TOTAL_RECORDS = "totalRecords";
  public static final Header X_OKAPI_USER_ID = new Header(OKAPI_USERID_HEADER, "d1d0a10b-c563-4c4b-ae22-e5a0c11623eb");

}
