package com.braincraft.payments.ledger

import com.braincraft.payments.metrics.MetricsService
import com.braincraft.payments.model.LedgerDirection
import com.braincraft.payments.repo.LedgerEntryInsert
import com.braincraft.payments.repo.LedgerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LedgerService(
  private val ledger: LedgerRepository,
  private val validator: LedgerValidator,
  private val metrics: MetricsService
) {
  @Transactional
  fun recordPayment(externalRef: String?, amountCents: Int, currency: String) {
    val cash = ledger.getAccountByCode("CASH") ?: throw IllegalStateException("CASH account missing")
    val revenue = ledger.getAccountByCode("REVENUE") ?: throw IllegalStateException("REVENUE account missing")

    val entries = listOf(
      LedgerEntryInsert(cash.id, LedgerDirection.DEBIT, amountCents, currency),
      LedgerEntryInsert(revenue.id, LedgerDirection.CREDIT, amountCents, currency)
    )
    validator.ensureBalanced(entries)

    val tx = ledger.createTransaction(externalRef)
    ledger.createEntries(tx.id, entries)
    metrics.recordLedgerTransaction()
  }

  @Transactional
  fun recordRefund(externalRef: String?, amountCents: Int, currency: String) {
    val cash = ledger.getAccountByCode("CASH") ?: throw IllegalStateException("CASH account missing")
    val refunds = ledger.getAccountByCode("REFUNDS") ?: throw IllegalStateException("REFUNDS account missing")

    val entries = listOf(
      LedgerEntryInsert(refunds.id, LedgerDirection.DEBIT, amountCents, currency),
      LedgerEntryInsert(cash.id, LedgerDirection.CREDIT, amountCents, currency)
    )
    validator.ensureBalanced(entries)

    val tx = ledger.createTransaction(externalRef)
    ledger.createEntries(tx.id, entries)
    metrics.recordLedgerTransaction()
  }
}
