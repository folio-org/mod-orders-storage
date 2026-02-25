package org.folio.services.batch;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.folio.HttpStatus;
import org.folio.models.CriterionBuilder;
import org.folio.models.TableNames;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.BatchTracking;
import org.folio.rest.persist.Conn;
import org.folio.util.DbUtils;

import java.util.Date;
import java.util.UUID;

@Log4j2
public class BatchTrackingService {

  private static final String BATCH_TRACKING_PROGRESS_INCREASE_SQL =
    "UPDATE %s SET jsonb = jsonb || jsonb_build_object('processed_count', (jsonb->>'processed_count')::int + 1) WHERE id = $1 RETURNING jsonb";
  private static final Triple<String, String, String> BATCH_TRACKING_CLEANUP_CRITERIA =
    Triple.of("created_date", "<", "NOW() - INTERVAL '%s hours'");

  /**
   * Creates a new batch tracking record.
   *
   * @param conn          the sql connection from transaction
   * @param batchTracking the batch tracking entity to create
   * @return future with the created batch tracking record, including generated batch id and created date
   */
  public Future<BatchTracking> createBatchTracking(Conn conn, BatchTracking batchTracking) {
    if (StringUtils.isBlank(batchTracking.getId())) {
      batchTracking.setId(UUID.randomUUID().toString());
    }
    batchTracking.setCreatedDate(new Date());
    return conn.saveAndReturnUpdatedEntity(TableNames.BATCH_TRACKING_TABLE, batchTracking.getId(), batchTracking)
      .onSuccess(rowSet -> log.info("createBatchTracking:: Batch tracking successfully created: '{}'", batchTracking.getId()))
      .onFailure(e -> log.error("createBatchTracking:: Batch tracking creation failed: '{}'", batchTracking.getId(), e));
  }

  /**
   * Increases the processed count for a batch tracking record and returns the updated record to check if the batch is complete.
   *
   * @param conn     the sql connection from transaction
   * @param batchId  the batch id
   * @param tenantId the tenant id
   * @return future with batch completion status (true if all records processed)
   */
  public Future<BatchTracking> increaseBatchTrackingProgress(Conn conn, String batchId, String tenantId) {
    var query = BATCH_TRACKING_PROGRESS_INCREASE_SQL.formatted(getFullTableName(tenantId, TableNames.BATCH_TRACKING_TABLE));
    return conn.execute(query, Tuple.of(batchId))
      .map(rowSet -> DbUtils.getRowSetAsEntity(rowSet, BatchTracking.class))
      .compose(batchTracking -> batchTracking != null
        ? Future.succeededFuture(batchTracking)
        : Future.failedFuture(new HttpException(HttpStatus.SC_NOT_FOUND, "Batch tracking not found for batchId: " + batchId)))
      .onFailure(t -> log.error("increaseBatchTrackingProgress:: Batch tracking progress increase failed", t));
  }

  /**
   * Deletes a batch tracking record by batch id.
   *
   * @param conn    the sql connection from transaction
   * @param batchId the batch id
   * @return future that completes when the record is deleted
   */
  public Future<Void> deleteBatchTracking(Conn conn, String batchId) {
    return conn.delete(TableNames.BATCH_TRACKING_TABLE, batchId)
      .compose(DbUtils::failOnNoUpdateOrDelete)
      .onSuccess(rowSet -> log.info("deleteBatchTracking:: Batch tracking successfully deleted: '{}'", batchId))
      .onFailure(e -> log.error("deleteBatchTracking:: Batch tracking deletion failed: '{}'", batchId, e))
      .mapEmpty();
  }

  /**
   * Deletes batch tracking records older than the specified timestamp.
   *
   * @param conn  the sql connection from transaction
   * @param hours the age threshold in hours for deleting old records
   * @return future with the number of records deleted
   */
  public Future<Long> cleanupBatchTrackings(Conn conn, int hours) {
    log.info("cleanupBatchTrackings:: Starting cleanup of batch trackings older than {} hours", hours);
    var criteria = new CriterionBuilder()
      .withJson(BATCH_TRACKING_CLEANUP_CRITERIA.getLeft(), BATCH_TRACKING_CLEANUP_CRITERIA.getMiddle(), BATCH_TRACKING_CLEANUP_CRITERIA.getRight().formatted(hours))
      .build();
    return conn.delete(TableNames.BATCH_TRACKING_TABLE, criteria)
      .map(DbUtils::getRowSetAsCount)
      .onSuccess(count -> log.info("cleanupBatchTrackings:: Cleaned up {} batch trackings", count))
      .onFailure(t -> log.warn("cleanupBatchTrackings:: Failed to clean up during batch trackings", t));
  }

}
