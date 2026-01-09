package com.braincraft.payments.ledger

import com.braincraft.payments.model.LedgerDirection
import com.braincraft.payments.repo.LedgerEntryInsert
import org.springframework.stereotype.Component

@Component
class LedgerValidator {
  fun ensureBalanced(entries: List<LedgerEntryInsert>) {
    val debit = entries.filter { it.direction == LedgerDirection.DEBIT }.sumOf { it.amountCents }
    val credit = entries.filter { it.direction == LedgerDirection.CREDIT }.sumOf { it.amountCents }
    require(debit == credit) { "ledger entries must balance" }
  }
}
