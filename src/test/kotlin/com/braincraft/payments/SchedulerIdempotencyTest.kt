package com.braincraft.payments

import com.braincraft.payments.model.SubscriptionStatus
import com.braincraft.payments.scheduler.RenewalService
import com.braincraft.payments.util.TimeUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.UUID

class SchedulerIdempotencyTest : IntegrationTestBase() {
  @Autowired lateinit var renewalService: RenewalService

  @Test
  fun schedulerDoesNotCreateDuplicateInvoices() {
    val customerId = createCustomer("renew@example.com")
    val planId = createPlan("Monthly", 2500, "USD", "day", 1)
    val start = Instant.now().minusSeconds(86400)
    val end = TimeUtils.addInterval(start, "day", 1)

    val subscriptionId = subscriptions.create(customerId, planId, SubscriptionStatus.ACTIVE, start, end).id
    invoices.create(subscriptionId, 2500, "USD", com.braincraft.payments.model.InvoiceStatus.PENDING, start, end)

    renewalService.runOnce(10)
    renewalService.runOnce(10)

    val count = jdbc.queryForObject(
      "SELECT COUNT(*) FROM invoices WHERE subscription_id = :id",
      mapOf("id" to subscriptionId),
      Long::class.java
    )
    assertThat(count).isEqualTo(2L)
  }

  @Test
  fun invoicePeriodUniquenessIsEnforced() {
    val customerId = createCustomer("unique@example.com")
    val planId = createPlan("Daily", 1100, "USD", "day", 1)
    val start = Instant.now().minusSeconds(86400)
    val end = TimeUtils.addInterval(start, "day", 1)

    val subscriptionId = subscriptions.create(customerId, planId, SubscriptionStatus.ACTIVE, start, end).id
    invoices.create(subscriptionId, 1100, "USD", com.braincraft.payments.model.InvoiceStatus.PENDING, start, end)

    try {
      invoices.create(subscriptionId, 1100, "USD", com.braincraft.payments.model.InvoiceStatus.PENDING, start, end)
    } catch (ex: DataIntegrityViolationException) {
      assertThat(ex.message).contains("duplicate")
      return
    }

    throw AssertionError("expected duplicate invoice to fail")
  }

  private fun createCustomer(email: String): UUID {
    val customer = customers.create(email)
    return customer.id
  }

  private fun createPlan(
    name: String,
    amountCents: Int,
    currency: String,
    intervalUnit: String,
    intervalCount: Int
  ): UUID {
    val plan = plans.create(name, amountCents, currency, intervalUnit, intervalCount)
    return plan.id
  }
}
