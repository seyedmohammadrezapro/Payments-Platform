package com.braincraft.payments.repo

import com.braincraft.payments.model.Invoice
import com.braincraft.payments.model.InvoiceStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class InvoiceRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    Invoice(
      id = rs.getObject("id", UUID::class.java),
      subscriptionId = rs.getObject("subscription_id", UUID::class.java),
      amountCents = rs.getInt("amount_cents"),
      currency = rs.getString("currency"),
      status = InvoiceStatus.valueOf(rs.getString("status").uppercase()),
      periodStart = rs.getTimestamp("period_start").toInstant(),
      periodEnd = rs.getTimestamp("period_end").toInstant(),
      createdAt = rs.getTimestamp("created_at").toInstant(),
      updatedAt = rs.getTimestamp("updated_at").toInstant(),
      paidAt = rs.getTimestamp("paid_at")?.toInstant()
    )
  }

  fun create(
    subscriptionId: UUID,
    amountCents: Int,
    currency: String,
    status: InvoiceStatus,
    periodStart: Instant,
    periodEnd: Instant
  ): Invoice {
    val id = jdbc.queryForObject(
      """
      INSERT INTO invoices (subscription_id, amount_cents, currency, status, period_start, period_end)
      VALUES (:subscription_id, :amount_cents, :currency, :status, :period_start, :period_end)
      RETURNING id
      """.trimIndent(),
      mapOf(
        "subscription_id" to subscriptionId,
        "amount_cents" to amountCents,
        "currency" to currency,
        "status" to status.name.lowercase(),
        "period_start" to periodStart,
        "period_end" to periodEnd
      ),
      UUID::class.java
    )
    return findById(id!!)!!
  }

  fun findById(id: UUID): Invoice? {
    val rows = jdbc.query(
      "SELECT * FROM invoices WHERE id = :id",
      mapOf("id" to id),
      mapper
    )
    return rows.firstOrNull()
  }

  fun listBySubscription(
    subscriptionId: UUID?,
    status: InvoiceStatus?,
    limit: Int,
    cursor: Instant?
  ): List<Invoice> {
    val where = StringBuilder("WHERE 1=1")
    val params = mutableMapOf<String, Any>()
    if (subscriptionId != null) {
      where.append(" AND subscription_id = :subscription_id")
      params["subscription_id"] = subscriptionId
    }
    if (status != null) {
      where.append(" AND status = :status")
      params["status"] = status.name.lowercase()
    }
    if (cursor != null) {
      where.append(" AND created_at < :cursor")
      params["cursor"] = cursor
    }
    val sql = "SELECT * FROM invoices $where ORDER BY created_at DESC LIMIT :limit"
    params["limit"] = limit
    return jdbc.query(sql, params, mapper)
  }

  fun updateStatus(id: UUID, status: InvoiceStatus, paidAt: Instant?) {
    jdbc.update(
      """
      UPDATE invoices
      SET status = :status,
          paid_at = :paid_at,
          updated_at = now()
      WHERE id = :id
      """.trimIndent(),
      mapOf(
        "id" to id,
        "status" to status.name.lowercase(),
        "paid_at" to paidAt
      )
    )
  }

  fun countByStatus(status: InvoiceStatus): Long {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM invoices WHERE status = :status",
      mapOf("status" to status.name.lowercase()),
      Long::class.java
    ) ?: 0L
  }
}
