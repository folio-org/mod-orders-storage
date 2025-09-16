package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.util.List;

import org.folio.rest.jaxrs.model.PoLineNumber;
import org.folio.rest.jaxrs.model.Title;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TitleSequenceNumberTest extends TestBase {

  private static final String TITLE_ENDPOINT = "/orders-storage/titles";
  private static final String TITLE_BY_ID_ENDPOINT = TITLE_ENDPOINT + "/{id}";
  private static final String TITLE_SEQUENCE_NUMBERS_ENDPOINT = TITLE_BY_ID_ENDPOINT + "/sequence-numbers";
  private static final String TITLE_SAMPLE_FILE_PATH = "mockdata/titles/title.json";

  @Test
  public void testGenerateTitleSequenceNumbers() throws MalformedURLException {
    String poId = postData(PURCHASE_ORDER.getEndpoint(), getFile(PURCHASE_ORDER.getSampleFileName()))
      .then().extract().path("id");
    String poLineId = postData(PO_LINE.getEndpoint(), getFile(PO_LINE.getSampleFileName()))
      .then().extract().path("id");
    String titleId = postData(TITLE_ENDPOINT, getFile(TITLE_SAMPLE_FILE_PATH))
      .then().statusCode(201).extract().path("id");

    var title = getTitle(titleId);
    assertEquals(1, title.getNextSequenceNumber());

    var sequenceNumbers = generateTitleSequenceNumbers(titleId, 1);
    assertEquals(List.of("1"), sequenceNumbers);

    title = getTitle(titleId);
    assertEquals(2, title.getNextSequenceNumber());

    sequenceNumbers = generateTitleSequenceNumbers(titleId, 3);
    assertEquals(List.of("2", "3", "4"), sequenceNumbers);

    title = getTitle(titleId);
    assertEquals(5, title.getNextSequenceNumber());

    deleteDataSuccess(PO_LINE.getEndpointWithId(), poLineId);
    deleteDataSuccess(PURCHASE_ORDER.getEndpointWithId(), poId);
  }

  private List<String> generateTitleSequenceNumbers(String id, int sequenceNumbers) throws MalformedURLException {
    return given().accept(ContentType.JSON)
      .pathParam("id", id)
      .queryParam("sequenceNumbers", sequenceNumbers)
      .header(TENANT_HEADER)
      .post(storageUrl(TITLE_SEQUENCE_NUMBERS_ENDPOINT))
      .then().statusCode(200)
      .extract().as(PoLineNumber.class)
      .getSequenceNumbers();
  }

  private Title getTitle(String id) throws MalformedURLException {
    return getDataById(TITLE_BY_ID_ENDPOINT, id)
      .then().statusCode(200)
      .body("id", equalTo(id))
      .extract().as(Title.class);
  }

}
