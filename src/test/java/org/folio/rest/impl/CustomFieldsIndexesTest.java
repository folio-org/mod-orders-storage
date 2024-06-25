package org.folio.rest.impl;

import static io.vertx.core.json.Json.encode;
import static java.lang.String.join;
import static org.folio.rest.utils.TestEntities.CUSTOM_FIELDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.folio.StorageTestSuite;
import org.folio.rest.jaxrs.model.CustomField;
import org.folio.rest.jaxrs.model.CustomField.Type;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class CustomFieldsIndexesTest extends TestBase {

  private static final String SCHEMA = join("_", TENANT_NAME, ModuleName.getModuleName());
  private static PostgresClient pgClient;

  @BeforeAll
  static void beforeAll() {
    pgClient = PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME);
  }

  private boolean hasIndex(String tableName, String indexName) {
    RowSet<Row> rows;
    try {
      rows =
        pgClient
          .execute(
            "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = $1 AND "
              + "tablename = $2 AND indexname = $3)",
            Tuple.of(SCHEMA, tableName, indexName))
          .toCompletionStage()
          .toCompletableFuture()
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    return rows.iterator().next().getBoolean(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"purchase_order", "po_line"})
  void testThatIndexesAreCreatedAndDropped(String tableName) throws MalformedURLException {
    String textboxId = UUID.randomUUID().toString();
    CustomField textbox =
      new CustomField()
        .withId(textboxId)
        .withName("text")
        .withType(Type.TEXTBOX_SHORT)
        .withEntityType(tableName);

    postData(CUSTOM_FIELDS.getEndpoint(), encode(textbox)).then().statusCode(201);
    assertTrue(hasIndex(tableName, tableName + "_custom_fields_text_idx_gin"));
    assertFalse(hasIndex(tableName, tableName + "_custom_fields_text_idx"));
    deleteData(CUSTOM_FIELDS.getEndpointWithId(), textboxId).then().statusCode(204);
    assertFalse(hasIndex(tableName, tableName + "_custom_fields_text_idx_gin"));
    assertFalse(hasIndex(tableName, tableName + "_custom_fields_text_idx"));

    String datepickerId = UUID.randomUUID().toString();
    CustomField datepicker =
      new CustomField()
        .withId(datepickerId)
        .withName("date")
        .withType(Type.DATE_PICKER)
        .withEntityType(tableName);

    postData(CUSTOM_FIELDS.getEndpoint(), encode(datepicker)).then().statusCode(201);
    assertTrue(hasIndex(tableName, tableName + "_custom_fields_date_idx_gin"));
    assertTrue(hasIndex(tableName, tableName + "_custom_fields_date_idx"));
    deleteData(CUSTOM_FIELDS.getEndpointWithId(), datepickerId).then().statusCode(204);
    assertFalse(hasIndex(tableName, tableName + "_custom_fields_date_idx_gin"));
    assertFalse(hasIndex(tableName, tableName + "_custom_fields_date_idx"));
  }
}
