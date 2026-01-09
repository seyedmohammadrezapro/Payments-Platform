package com.braincraft.payments.repo

import com.braincraft.payments.model.Payment
import com.braincraft.payments.model.PaymentStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class PaymentRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    Payment(
      id = rs.getObject("id", UUID::class.java),
      invoiceId = rs.getObject("invoice_id", UUID::class.java),
      providerPaymentId = rs.getString("provider_payment_id"),
      amountCents = rs.getInt("amount_cents"),
      currency = rs.getString("currency"),
      status = PaymentStatus.valueOf(rs.getString("status").uppercase()),
      createdAt = rs.getTimestamp("created_at").toInstant()
    )
  }

  fun create(
    invoiceId: UUID,
    providerPaymentId: String?,
    amountCents: Int,
    currency: String,
    status: PaymentStatus
  ): Payment {
    val id = jdbc.queryForObject(
      """
      INSERT INTO payments (invoice_id, provider_payment_id, amount_cents, currency, status)
      VALUES (:invoice_id, :provider_payment_id, :amount_cents, :currency, :status)
      RETURNING id
      """.trimIndent(),
      mapOf(
        "invoice_id" to invoiceId,
        "provider_payment_id" to providerPaymentId,
        "amount_cents" to amountCents,
        "currency" to currency,
        "status" to status.name.lowercase()
      ),
      UUID::class.java
    )
    return findById(id!!)!!
  }

  fun findById(id: UUID): Payment? {
    val rows = jdbc.query(
      "SELECT * FROM payments WHERE id = :id",
      mapOf("id" to id),
      mapper
    )
    return rows.firstOrNull()
  }

  fun findByInvoice(invoiceId: UUID): List<Payment> {
    return jdbc.query(
      "SELECT * FROM payments WHERE invoice_id = :invoice_id ORDER BY created_at DESC",
      mapOf("invoice_id" to invoiceId),
      mapper
    )
  }
}
