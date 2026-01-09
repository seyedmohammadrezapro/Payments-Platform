package com.braincraft.payments.service

import com.braincraft.payments.model.Invoice
import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.model.Subscription
import com.braincraft.payments.model.SubscriptionStatus
import com.braincraft.payments.repo.InvoiceRepository
import com.braincraft.payments.repo.PlanRepository
import com.braincraft.payments.repo.SubscriptionRepository
import com.braincraft.payments.repo.CustomerRepository
import com.braincraft.payments.util.TimeUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class SubscriptionService(
  private val customers: CustomerRepository,
  private val plans: PlanRepository,
  private val subscriptions: SubscriptionRepository,
  private val invoices: InvoiceRepository,
  private val clock: Clock
) {
  data class SubscriptionCreateResult(val subscription: Subscription, val invoice: Invoice)

  @Transactional
  fun createSubscription(customerId: UUID, planId: UUID, startAt: Instant?): SubscriptionCreateResult {
    val customer = customers.findById(customerId)
      ?: throw IllegalArgumentException("customer not found")
    val plan = plans.findById(planId)
      ?: throw IllegalArgumentException("plan not found")

    val start = startAt ?: Instant.now(clock)
    val end = TimeUtils.addInterval(start, plan.intervalUnit, plan.intervalCount)

    val subscription = subscriptions.create(
      customerId = customer.id,
      planId = plan.id,
      status = SubscriptionStatus.PENDING,
      periodStart = start,
      periodEnd = end
    )

    val invoice = invoices.create(
      subscriptionId = subscription.id,
      amountCents = plan.amountCents,
      currency = plan.currency,
      status = InvoiceStatus.PENDING,
      periodStart = start,
      periodEnd = end
    )

    return SubscriptionCreateResult(subscription, invoice)
  }

  fun cancelSubscription(subscriptionId: UUID, cancelAtPeriodEnd: Boolean) {
    val current = subscriptions.findById(subscriptionId)
      ?: throw IllegalArgumentException("subscription not found")
    val nextStatus = if (cancelAtPeriodEnd) current.status else SubscriptionStatus.CANCELLED
    subscriptions.updateStatus(subscriptionId, nextStatus, null, null, cancelAtPeriodEnd)
  }

  fun findById(subscriptionId: UUID): Subscription? = subscriptions.findById(subscriptionId)
}
