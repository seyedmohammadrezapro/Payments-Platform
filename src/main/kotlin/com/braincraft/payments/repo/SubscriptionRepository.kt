package com.braincraft.payments.repo

import com.braincraft.payments.model.Subscription
import com.braincraft.payments.model.SubscriptionStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class SubscriptionRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    Subscription(
      id = rs.getObject("id", UUID::class.java),
      customerId = rs.getObject("customer_id", UUID::class.java),
      planId = rs.getObject("plan_id", UUID::class.java),
      status = SubscriptionStatus.valueOf(rs.getString("status").uppercase()),
      currentPeriodStart = rs.getTimestamp("current_period_start")?.toInstant(),
      currentPeriodEnd = rs.getTimestamp("current_period_end")?.toInstant(),
      cancelAtPeriodEnd = rs.getBoolean("cancel_at_period_end"),
      createdAt = rs.getTimestamp("created_at").toInstant(),
      updatedAt = rs.getTimestamp("updated_at").toInstant()
    )
  }

  fun create(
    customerId: UUID,
    planId: UUID,
    status: SubscriptionStatus,
    periodStart: Instant?,
    periodEnd: Instant?
  ): Subscription {
    val id = jdbc.queryForObject(
      """
      INSERT INTO subscriptions (customer_id, plan_id, status, current_period_start, current_period_end)
      VALUES (:customer_id, :plan_id, :status, :start, :end)
      RETURNING id
      """.trimIndent(),
      mapOf(
        "customer_id" to customerId,
        "plan_id" to planId,
        "status" to status.name.lowercase(),
        "start" to periodStart,
        "end" to periodEnd
      ),
      UUID::class.java
    )
    return findById(id!!)!!
  }

  fun findById(id: UUID): Subscription? {
    val rows = jdbc.query(
      "SELECT * FROM subscriptions WHERE id = :id",
      mapOf("id" to id),
      mapper
    )
    return rows.firstOrNull()
  }

  fun findDueRenewals(now: Instant, limit: Int): List<Subscription> {
    return jdbc.query(
      """
      SELECT * FROM subscriptions
      WHERE status IN ('active', 'past_due')
        AND cancel_at_period_end = false
        AND current_period_end IS NOT NULL
        AND current_period_end <= :now
      ORDER BY current_period_end ASC
      LIMIT :limit
      """.trimIndent(),
      mapOf("now" to now, "limit" to limit),
      mapper
    )
  }

  fun updatePeriods(id: UUID, periodStart: Instant, periodEnd: Instant) {
    jdbc.update(
      """
      UPDATE subscriptions
      SET current_period_start = :start,
          current_period_end = :end,
          updated_at = now()
      WHERE id = :id
      """.trimIndent(),
      mapOf("id" to id, "start" to periodStart, "end" to periodEnd)
    )
  }

  fun updateStatus(
    id: UUID,
    status: SubscriptionStatus,
    periodStart: Instant?,
    periodEnd: Instant?,
    cancelAtPeriodEnd: Boolean?
  ) {
    jdbc.update(
      """
      UPDATE subscriptions
      SET status = :status,
          current_period_start = COALESCE(:start, current_period_start),
          current_period_end = COALESCE(:end, current_period_end),
          cancel_at_period_end = COALESCE(:cancel_at_period_end, cancel_at_period_end),
          updated_at = now()
      WHERE id = :id
      """.trimIndent(),
      mapOf(
        "id" to id,
        "status" to status.name.lowercase(),
        "start" to periodStart,
        "end" to periodEnd,
        "cancel_at_period_end" to cancelAtPeriodEnd
      )
    )
  }

  fun countByStatus(status: SubscriptionStatus): Long {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM subscriptions WHERE status = :status",
      mapOf("status" to status.name.lowercase()),
      Long::class.java
    ) ?: 0L
  }
}
