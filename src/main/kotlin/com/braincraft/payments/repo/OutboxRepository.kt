package com.braincraft.payments.repo

import com.braincraft.payments.model.OutboxJob
import com.braincraft.payments.model.OutboxStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
class OutboxRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    OutboxJob(
      id = rs.getObject("id", UUID::class.java),
      aggregateType = rs.getString("aggregate_type"),
      aggregateId = rs.getString("aggregate_id"),
      type = rs.getString("type"),
      payloadJson = rs.getString("payload_json"),
      status = OutboxStatus.valueOf(rs.getString("status").uppercase()),
      availableAt = rs.getTimestamp("available_at").toInstant(),
      attempts = rs.getInt("attempts"),
      lastError = rs.getString("last_error"),
      createdAt = rs.getTimestamp("created_at").toInstant(),
      updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
  }

  fun enqueue(aggregateType: String, aggregateId: String, type: String, payloadJson: String) {
    jdbc.update(
      """
      INSERT INTO outbox_jobs (aggregate_type, aggregate_id, type, payload_json, status)
      VALUES (:aggregate_type, :aggregate_id, :type, CAST(:payload_json AS jsonb), 'pending')
      """.trimIndent(),
      mapOf(
        "aggregate_type" to aggregateType,
        "aggregate_id" to aggregateId,
        "type" to type,
        "payload_json" to payloadJson
      )
    )
  }

  fun claimBatch(limit: Int): List<OutboxJob> {
    val sql = """
      WITH cte AS (
        SELECT id FROM outbox_jobs
        WHERE status = 'pending' AND available_at <= now()
        ORDER BY available_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
      )
      UPDATE outbox_jobs
      SET status = 'processing', updated_at = now()
      WHERE id IN (SELECT id FROM cte)
      RETURNING *
    """.trimIndent()
    return jdbc.query(sql, mapOf("limit" to limit), mapper)
  }

  fun markSucceeded(id: UUID) {
    jdbc.update(
      "UPDATE outbox_jobs SET status = 'succeeded', updated_at = now() WHERE id = :id",
      mapOf("id" to id)
    )
  }

  fun markFailed(id: UUID, attempts: Int, lastError: String, nextAvailableAt: Instant) {
    jdbc.update(
      """
      UPDATE outbox_jobs
      SET status = 'pending',
          attempts = :attempts,
          last_error = :last_error,
          available_at = :available_at,
          updated_at = now()
      WHERE id = :id
      """.trimIndent(),
      mapOf(
        "id" to id,
        "attempts" to attempts,
        "last_error" to lastError,
        "available_at" to nextAvailableAt.atOffset(ZoneOffset.UTC)
      )
    )
  }

  fun markDead(id: UUID, attempts: Int, lastError: String) {
    jdbc.update(
      """
      UPDATE outbox_jobs
      SET status = 'dead',
          attempts = :attempts,
          last_error = :last_error,
          updated_at = now()
      WHERE id = :id
      """.trimIndent(),
      mapOf(
        "id" to id,
        "attempts" to attempts,
        "last_error" to lastError
      )
    )
  }

  fun countPending(): Long {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM outbox_jobs WHERE status = 'pending' AND available_at <= now()",
      emptyMap<String, Any>(),
      Long::class.java
    ) ?: 0L
  }
}
