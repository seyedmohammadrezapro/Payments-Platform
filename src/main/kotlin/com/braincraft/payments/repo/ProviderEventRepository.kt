package com.braincraft.payments.repo

import com.braincraft.payments.model.ProviderEvent
import com.braincraft.payments.model.ProviderEventStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class ProviderEventRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    ProviderEvent(
      id = rs.getObject("id", UUID::class.java),
      eventId = rs.getString("event_id"),
      type = rs.getString("type"),
      rawJson = rs.getString("raw_json"),
      status = ProviderEventStatus.valueOf(rs.getString("status").uppercase()),
      receivedAt = rs.getTimestamp("received_at").toInstant(),
      processedAt = rs.getTimestamp("processed_at")?.toInstant(),
      attempts = rs.getInt("attempts"),
      lastError = rs.getString("last_error")
    )
  }

  fun insertIfAbsent(eventId: String, type: String, rawJson: String): Boolean {
    val updated = jdbc.update(
      """
      INSERT INTO provider_events (event_id, type, raw_json, status)
      VALUES (:event_id, :type, CAST(:raw_json AS jsonb), 'received')
      ON CONFLICT (event_id) DO NOTHING
      """.trimIndent(),
      mapOf("event_id" to eventId, "type" to type, "raw_json" to rawJson)
    )
    return updated > 0
  }

  fun findByEventId(eventId: String): ProviderEvent? {
    val rows = jdbc.query(
      "SELECT * FROM provider_events WHERE event_id = :event_id",
      mapOf("event_id" to eventId),
      mapper
    )
    return rows.firstOrNull()
  }

  fun updateStatus(
    eventId: String,
    status: ProviderEventStatus,
    attempts: Int,
    lastError: String?,
    processedAt: Instant?
  ) {
    jdbc.update(
      """
      UPDATE provider_events
      SET status = :status,
          attempts = :attempts,
          last_error = :last_error,
          processed_at = :processed_at
      WHERE event_id = :event_id
      """.trimIndent(),
      mapOf(
        "event_id" to eventId,
        "status" to status.name.lowercase(),
        "attempts" to attempts,
        "last_error" to lastError,
        "processed_at" to processedAt
      )
    )
  }

  fun list(status: ProviderEventStatus?, limit: Int, cursor: Instant?): List<ProviderEvent> {
    val where = StringBuilder("WHERE 1=1")
    val params = mutableMapOf<String, Any>()
    if (status != null) {
      where.append(" AND status = :status")
      params["status"] = status.name.lowercase()
    }
    if (cursor != null) {
      where.append(" AND received_at < :cursor")
      params["cursor"] = cursor
    }
    params["limit"] = limit
    val sql = "SELECT * FROM provider_events $where ORDER BY received_at DESC LIMIT :limit"
    return jdbc.query(sql, params, mapper)
  }

  fun countAll(): Long {
    return jdbc.queryForObject("SELECT COUNT(*) FROM provider_events", emptyMap<String, Any>(), Long::class.java)
      ?: 0L
  }

  fun countByStatus(status: ProviderEventStatus): Long {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM provider_events WHERE status = :status",
      mapOf("status" to status.name.lowercase()),
      Long::class.java
    ) ?: 0L
  }

  fun countWithAttempts(minAttempts: Int): Long {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM provider_events WHERE attempts >= :min_attempts",
      mapOf("min_attempts" to minAttempts),
      Long::class.java
    ) ?: 0L
  }

  fun processingP95Seconds(): Double {
    return jdbc.queryForObject(
      """
      SELECT COALESCE(
        percentile_cont(0.95) WITHIN GROUP (
          ORDER BY EXTRACT(EPOCH FROM (processed_at - received_at))
        ),
        0
      )
      FROM provider_events
      WHERE processed_at IS NOT NULL
      """.trimIndent(),
      emptyMap<String, Any>(),
      Double::class.java
    ) ?: 0.0
  }
}
