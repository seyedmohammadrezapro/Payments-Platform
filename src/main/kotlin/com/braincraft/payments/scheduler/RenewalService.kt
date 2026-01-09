package com.braincraft.payments.scheduler

import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.repo.InvoiceRepository
import com.braincraft.payments.repo.PlanRepository
import com.braincraft.payments.repo.SubscriptionRepository
import com.braincraft.payments.util.TimeUtils
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

@Service
class RenewalService(
  private val subscriptions: SubscriptionRepository,
  private val plans: PlanRepository,
  private val invoices: InvoiceRepository,
  private val clock: Clock,
  transactionManager: PlatformTransactionManager
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val tx = TransactionTemplate(transactionManager)

  fun runOnce(limit: Int): Int {
    val now = Instant.now(clock)
    val due = subscriptions.findDueRenewals(now, limit)
    if (due.isEmpty()) return 0

    due.forEach { sub ->
      try {
        tx.executeWithoutResult {
          val plan = plans.findById(sub.planId) ?: return@executeWithoutResult
          val periodStart = sub.currentPeriodEnd ?: return@executeWithoutResult
          val periodEnd = TimeUtils.addInterval(periodStart, plan.intervalUnit, plan.intervalCount)

          try {
            invoices.create(
              subscriptionId = sub.id,
              amountCents = plan.amountCents,
              currency = plan.currency,
              status = InvoiceStatus.PENDING,
              periodStart = periodStart,
              periodEnd = periodEnd
            )
            subscriptions.updatePeriods(sub.id, periodStart, periodEnd)
          } catch (ex: DataIntegrityViolationException) {
            log.info("invoice already exists for period", ex)
          }
        }
      } catch (ex: Exception) {
        log.error("renewal processing failed", ex)
      }
    }

    return due.size
  }
}
