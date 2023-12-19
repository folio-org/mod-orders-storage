package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestData.CustomFields.POL;
import static org.folio.rest.utils.TestEntities.CUSTOM_FIELDS;

import java.net.MalformedURLException;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomFieldOptionStatistic;
import org.folio.rest.jaxrs.model.CustomFieldStatistic;
import org.folio.rest.utils.IsolatedTenant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@code /stats} endpoints return a count of zero with {@link
 * org.folio.service.NoOpRecordService}
 */
@IsolatedTenant
public class CustomFieldsAPITest extends TestBase {

  private static final String STATS_ENDPOINT = "/custom-fields/{id}/stats";
  private static final String OPTIONS_STATS_ENDPOINT = "/custom-fields/{id}/options/{optId}/stats";
  private final CustomField customFieldPOLSample = getFileAsObject(POL, CustomField.class);

  @Test
  void testGetUsageCountCustomField() throws MalformedURLException {
    givenTestData(Pair.of(CUSTOM_FIELDS, POL));
    CustomFieldStatistic customFieldStatistic =
        given()
            .header(ISOLATED_TENANT_HEADER)
            .pathParam("id", customFieldPOLSample.getId())
            .get(storageUrl(STATS_ENDPOINT))
            .then()
            .statusCode(200)
            .extract()
            .as(CustomFieldStatistic.class);
    Assertions.assertEquals(0, customFieldStatistic.getCount());
  }

  @Test
  void testGetUsageCountCustomFieldOption() throws MalformedURLException {
    givenTestData(Pair.of(CUSTOM_FIELDS, POL));
    CustomFieldOptionStatistic customFieldOptionStatistic =
        given()
            .header(ISOLATED_TENANT_HEADER)
            .pathParams("id", customFieldPOLSample.getId(), "optId", "opt_0")
            .get(storageUrl(OPTIONS_STATS_ENDPOINT))
            .then()
            .statusCode(200)
            .extract()
            .as(CustomFieldOptionStatistic.class);
    Assertions.assertEquals(0, customFieldOptionStatistic.getCount());
  }
}
