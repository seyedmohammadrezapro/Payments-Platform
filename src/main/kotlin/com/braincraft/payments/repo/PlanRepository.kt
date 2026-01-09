package com.braincraft.payments.repo

import com.braincraft.payments.model.Plan
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class PlanRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    Plan(
      id = rs.getObject("id", UUID::class.java),
      name = rs.getString("name"),
      amountCents = rs.getInt("amount_cents"),
      currency = rs.getString("currency"),
      intervalUnit = rs.getString("interval_unit"),
      intervalCount = rs.getInt("interval_count"),
      createdAt = rs.getTimestamp("created_at").toInstant()
    )
  }

  fun create(name: String, amountCents: Int, currency: String, intervalUnit: String, intervalCount: Int): Plan {
    val id = jdbc.queryForObject(
      """
      INSERT INTO plans (name, amount_cents, currency, interval_unit, interval_count)
      VALUES (:name, :amount_cents, :currency, :interval_unit, :interval_count)
      RETURNING id
      """.trimIndent(),
      mapOf(
        "name" to name,
        "amount_cents" to amountCents,
        "currency" to currency,
        "interval_unit" to intervalUnit,
        "interval_count" to intervalCount
      ),
      UUID::class.java
    )
    return findById(id!!)!!
  }

  fun findById(id: UUID): Plan? {
    val rows = jdbc.query(
      "SELECT * FROM plans WHERE id = :id",
      mapOf("id" to id),
      mapper
    )
    return rows.firstOrNull()
  }
}
