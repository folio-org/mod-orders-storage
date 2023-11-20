package org.folio.rest.impl;

import io.restassured.http.Headers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.EMPTY;

public class ClaimingAPITest extends TestBase {

  private static final Logger log = LogManager.getLogger();
  private static final String PO_NUMBER_ENDPOINT = "/orders-storage/claiming/process";

  @Test
  void testPieceCreateUpdateEvents() throws MalformedURLException {
    log.info("--- mod-orders-storage claiming batch job test: start job");

    String userId = UUID.randomUUID().toString();
    Headers headers = getDikuTenantHeaders(userId);
    postData(PO_NUMBER_ENDPOINT, EMPTY, headers)
      .then()
      .statusCode(200);
  }
}
