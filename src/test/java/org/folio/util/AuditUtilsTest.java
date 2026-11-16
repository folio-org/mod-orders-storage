package org.folio.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import java.util.UUID;

import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.OrderLineSnapshot;
import org.folio.rest.jaxrs.model.PoLine;
import org.junit.jupiter.api.Test;

public class AuditUtilsTest {

  private static final String CREATED_USER_ID = UUID.randomUUID().toString();
  private static final String UPDATED_USER_ID = UUID.randomUUID().toString();
  private static final Date CREATED_DATE = new Date(1_000_000L);
  private static final Date UPDATED_DATE = new Date(2_000_000L);

  @Test
  public void testConvertToSnapshotKeepsMetadata() {
    var metadata = new Metadata()
      .withCreatedDate(CREATED_DATE)
      .withCreatedByUserId(CREATED_USER_ID)
      .withUpdatedDate(UPDATED_DATE)
      .withUpdatedByUserId(UPDATED_USER_ID);
    var poLine = new PoLine().withId(UUID.randomUUID().toString()).withMetadata(metadata);

    var snapshot = AuditUtils.convertToSnapshot(poLine, OrderLineSnapshot.class);

    assertNotNull(snapshot);
    assertNotNull(snapshot.getMetadata());
    assertEquals(CREATED_DATE, snapshot.getMetadata().getCreatedDate());
    assertEquals(CREATED_USER_ID, snapshot.getMetadata().getCreatedByUserId());
    assertEquals(UPDATED_DATE, snapshot.getMetadata().getUpdatedDate());
    assertEquals(UPDATED_USER_ID, snapshot.getMetadata().getUpdatedByUserId());
    // entity itself must not be modified
    assertNotNull(poLine.getMetadata());
  }

  @Test
  public void testConvertToSnapshotReturnsNullForNullEntity() {
    assertNull(AuditUtils.convertToSnapshot(null, OrderLineSnapshot.class));
  }

  @Test
  public void testCopyCreatedMetadata() {
    var originalMetadata = new Metadata()
      .withCreatedDate(CREATED_DATE)
      .withCreatedByUserId(CREATED_USER_ID);
    var metadata = new Metadata()
      .withCreatedDate(UPDATED_DATE)
      .withCreatedByUserId(UPDATED_USER_ID)
      .withUpdatedDate(UPDATED_DATE)
      .withUpdatedByUserId(UPDATED_USER_ID);

    AuditUtils.copyCreatedMetadata(originalMetadata, metadata);

    assertEquals(CREATED_DATE, metadata.getCreatedDate());
    assertEquals(CREATED_USER_ID, metadata.getCreatedByUserId());
    assertEquals(UPDATED_DATE, metadata.getUpdatedDate());
    assertEquals(UPDATED_USER_ID, metadata.getUpdatedByUserId());
  }

  @Test
  public void testCopyCreatedMetadataWithoutOriginal() {
    var metadata = new Metadata()
      .withCreatedDate(UPDATED_DATE)
      .withCreatedByUserId(UPDATED_USER_ID);

    AuditUtils.copyCreatedMetadata(null, metadata);

    assertEquals(UPDATED_DATE, metadata.getCreatedDate());
    assertEquals(UPDATED_USER_ID, metadata.getCreatedByUserId());
  }

}
