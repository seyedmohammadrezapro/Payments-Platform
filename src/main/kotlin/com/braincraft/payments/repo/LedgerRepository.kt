package com.braincraft.payments.repo

import com.braincraft.payments.model.LedgerAccount
import com.braincraft.payments.model.LedgerAccountType
import com.braincraft.payments.model.LedgerDirection
import com.braincraft.payments.model.LedgerEntry
import com.braincraft.payments.model.LedgerTransaction
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class LedgerRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val accountMapper = RowMapper { rs: ResultSet, _: Int ->
    LedgerAccount(
      id = rs.getObject("id", UUID::class.java),
      code = rs.getString("code"),
      name = rs.getString("name"),
      type = LedgerAccountType.valueOf(rs.getString("type").uppercase())
    )
  }

  private val txMapper = RowMapper { rs: ResultSet, _: Int ->
    LedgerTransaction(
      id = rs.getObject("id", UUID::class.java),
      externalRef = rs.getString("external_ref"),
      createdAt = rs.getTimestamp("created_at").toInstant()
    )
  }

  private val entryMapper = RowMapper { rs: ResultSet, _: Int ->
    LedgerEntry(
      id = rs.getObject("id", UUID::class.java),
      transactionId = rs.getObject("transaction_id", UUID::class.java),
      accountId = rs.getObject("account_id", UUID::class.java),
      direction = LedgerDirection.valueOf(rs.getString("direction").uppercase()),
      amountCents = rs.getInt("amount_cents"),
      currency = rs.getString("currency"),
      createdAt = rs.getTimestamp("created_at").toInstant()
    )
  }

  fun getAccountByCode(code: String): LedgerAccount? {
    val rows = jdbc.query(
      "SELECT * FROM ledger_accounts WHERE code = :code",
      mapOf("code" to code),
      accountMapper
    )
    return rows.firstOrNull()
  }

  fun createTransaction(externalRef: String?): LedgerTransaction {
    val id = jdbc.queryForObject(
      "INSERT INTO ledger_transactions (external_ref) VALUES (:external_ref) RETURNING id",
      mapOf("external_ref" to externalRef),
      UUID::class.java
    )
    return getTransaction(id!!)!!
  }

  fun getTransaction(id: UUID): LedgerTransaction? {
    val rows = jdbc.query(
      "SELECT * FROM ledger_transactions WHERE id = :id",
      mapOf("id" to id),
      txMapper
    )
    return rows.firstOrNull()
  }

  fun createEntries(transactionId: UUID, entries: List<LedgerEntryInsert>) {
    val sql = """
      INSERT INTO ledger_entries (transaction_id, account_id, direction, amount_cents, currency)
      VALUES (:transaction_id, :account_id, :direction, :amount_cents, :currency)
    """.trimIndent()
    val batch = entries.map {
      mapOf(
        "transaction_id" to transactionId,
        "account_id" to it.accountId,
        "direction" to it.direction.name.lowercase(),
        "amount_cents" to it.amountCents,
        "currency" to it.currency
      )
    }
    jdbc.batchUpdate(sql, batch.toTypedArray())
  }

  fun listTransactions(limit: Int, cursor: Instant?): List<LedgerTransaction> {
    val where = StringBuilder("WHERE 1=1")
    val params = mutableMapOf<String, Any>()
    if (cursor != null) {
      where.append(" AND created_at < :cursor")
      params["cursor"] = cursor
    }
    params["limit"] = limit
    val sql = "SELECT * FROM ledger_transactions $where ORDER BY created_at DESC LIMIT :limit"
    return jdbc.query(sql, params, txMapper)
  }

  fun listEntries(transactionId: UUID): List<LedgerEntry> {
    return jdbc.query(
      "SELECT * FROM ledger_entries WHERE transaction_id = :transaction_id",
      mapOf("transaction_id" to transactionId),
      entryMapper
    )
  }
}

data class LedgerEntryInsert(
  val accountId: UUID,
  val direction: LedgerDirection,
  val amountCents: Int,
  val currency: String
)
