package com.braincraft.payments

import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.model.SubscriptionStatus
import com.braincraft.payments.util.HashUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.time.Instant
import java.util.UUID

class PaymentFlowIntegrationTest : IntegrationTestBase() {
  @Test
  fun paymentSucceededFlow() {
    val customerId = createCustomer("user@example.com")
    val planId = createPlan("Starter", 1500, "USD", "day", 1)
    val subResult = createSubscription(customerId, planId, null)

    sendWebhook("payment_succeeded", subResult.invoiceId)
    outboxWorker.processBatch(10)

    val invoice = invoices.findById(subResult.invoiceId)
    val subscription = subscriptions.findById(subResult.subscriptionId)

    assertThat(invoice?.status).isEqualTo(InvoiceStatus.PAID)
    assertThat(subscription?.status).isEqualTo(SubscriptionStatus.ACTIVE)

    val invoicePayments = payments.findByInvoice(subResult.invoiceId)
    assertThat(invoicePayments).hasSize(1)

    val ledgerCount = jdbc.queryForObject(
      "SELECT COUNT(*) FROM ledger_transactions WHERE external_ref = :ref",
      mapOf("ref" to "invoice:${subResult.invoiceId}"),
      Long::class.java
    )
    assertThat(ledgerCount).isEqualTo(1L)
  }

  @Test
  fun webhookIdempotency() {
    val customerId = createCustomer("dup@example.com")
    val planId = createPlan("Basic", 1200, "USD", "day", 1)
    val subResult = createSubscription(customerId, planId, null)

    val eventId = "evt-${UUID.randomUUID()}"
    sendWebhook("payment_succeeded", subResult.invoiceId, eventId)
    outboxWorker.processBatch(10)

    sendWebhook("payment_succeeded", subResult.invoiceId, eventId)
    outboxWorker.processBatch(10)

    val invoicePayments = payments.findByInvoice(subResult.invoiceId)
    assertThat(invoicePayments).hasSize(1)

    val ledgerCount = jdbc.queryForObject(
      "SELECT COUNT(*) FROM ledger_transactions WHERE external_ref = :ref",
      mapOf("ref" to "invoice:${subResult.invoiceId}"),
      Long::class.java
    )
    assertThat(ledgerCount).isEqualTo(1L)
  }

  @Test
  fun retryToDeadLetter() {
    val eventId = "evt-dead-${UUID.randomUUID()}"
    sendWebhook("payment_succeeded", UUID.randomUUID(), eventId)

    outboxWorker.processBatch(10)
    val jobId = jdbc.queryForObject(
      "SELECT id FROM outbox_jobs WHERE aggregate_id = :event_id",
      mapOf("event_id" to eventId),
      UUID::class.java
    )
    jdbc.update(
      "UPDATE outbox_jobs SET available_at = now() WHERE id = :id",
      mapOf("id" to jobId)
    )

    outboxWorker.processBatch(10)

    val status = jdbc.queryForObject(
      "SELECT status FROM provider_events WHERE event_id = :event_id",
      mapOf("event_id" to eventId),
      String::class.java
    )
    assertThat(status).isEqualTo("dead")
  }

  private fun createCustomer(email: String): UUID {
    val body = mapOf("email" to email)
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    headers.set("Idempotency-Key", UUID.randomUUID().toString())

    val response = rest.postForEntity(url("/customers"), HttpEntity(body, headers), String::class.java)
    val node = objectMapper.readTree(response.body)
    return UUID.fromString(node.get("id").asText())
  }

  private fun createPlan(
    name: String,
    amountCents: Int,
    currency: String,
    intervalUnit: String,
    intervalCount: Int
  ): UUID {
    val body = mapOf(
      "name" to name,
      "amountCents" to amountCents,
      "currency" to currency,
      "intervalUnit" to intervalUnit,
      "intervalCount" to intervalCount
    )
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    headers.set("Idempotency-Key", UUID.randomUUID().toString())

    val response = rest.postForEntity(url("/plans"), HttpEntity(body, headers), String::class.java)
    val node = objectMapper.readTree(response.body)
    return UUID.fromString(node.get("id").asText())
  }

  private fun createSubscription(customerId: UUID, planId: UUID, startAt: Instant?): SubscriptionResult {
    val body = mutableMapOf(
      "customerId" to customerId,
      "planId" to planId
    )
    if (startAt != null) {
      body["startAt"] = startAt.toString()
    }

    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    headers.set("Idempotency-Key", UUID.randomUUID().toString())

    val response = rest.postForEntity(url("/subscriptions"), HttpEntity(body, headers), String::class.java)
    val node = objectMapper.readTree(response.body)
    val subscriptionId = UUID.fromString(node.get("subscription").get("id").asText())
    val invoiceId = UUID.fromString(node.get("invoice").get("id").asText())
    return SubscriptionResult(subscriptionId, invoiceId)
  }

  private fun sendWebhook(type: String, invoiceId: UUID, eventId: String = "evt-${UUID.randomUUID()}") {
    val payload = objectMapper.writeValueAsString(
      mapOf(
        "event_id" to eventId,
        "type" to type,
        "created_at" to Instant.now().toString(),
        "data" to mapOf(
          "invoice_id" to invoiceId.toString(),
          "provider_payment_id" to "pay-${UUID.randomUUID()}",
          "amount_cents" to 1500,
          "currency" to "USD"
        )
      )
    )

    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON
    headers.set("X-Signature", HashUtils.hmacSha256Hex("test_secret", payload.toByteArray()))

    rest.postForEntity(url("/webhooks/provider"), HttpEntity(payload, headers), String::class.java)
  }

  private data class SubscriptionResult(val subscriptionId: UUID, val invoiceId: UUID)
}
