package com.braincraft.payments.service

import com.braincraft.payments.ledger.LedgerService
import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.model.PaymentStatus
import com.braincraft.payments.model.ProviderEventStatus
import com.braincraft.payments.model.SubscriptionStatus
import com.braincraft.payments.repo.InvoiceRepository
import com.braincraft.payments.repo.PaymentRepository
import com.braincraft.payments.repo.ProviderEventRepository
import com.braincraft.payments.repo.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class ProviderEventProcessor(
  private val providerEvents: ProviderEventRepository,
  private val invoices: InvoiceRepository,
  private val subscriptions: SubscriptionRepository,
  private val payments: PaymentRepository,
  private val ledger: LedgerService,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) {
  fun now(): Instant = Instant.now(clock)

  fun nextRetryAt(attempt: Int): Instant {
    val base = 2L
    val jitter = (0..1000).random().toLong()
    val delaySeconds = base * (1L shl attempt) + jitter / 1000
    return Instant.now(clock).plusSeconds(delaySeconds)
  }

  @Transactional
  fun process(eventId: String) {
    val event = providerEvents.findByEventId(eventId)
      ?: throw IllegalArgumentException("event not found")

    providerEvents.updateStatus(eventId, ProviderEventStatus.PROCESSING, event.attempts, null, null)

    val root = objectMapper.readTree(event.rawJson)
    val type = root.get("type").asText()
    val data = root.get("data")
    val invoiceId = data?.get("invoice_id")?.asText()
      ?: throw IllegalArgumentException("invoice_id is required")

    val invoice = invoices.findById(UUID.fromString(invoiceId))
      ?: throw IllegalArgumentException("invoice not found")

    when (type) {
      "payment_succeeded" -> handlePaymentSucceeded(invoice, data)
      "payment_failed" -> handlePaymentFailed(invoice, data)
      "refund_succeeded" -> handleRefundSucceeded(invoice, data)
      else -> throw IllegalArgumentException("unsupported event type: $type")
    }
  }

  private fun handlePaymentSucceeded(invoice: com.braincraft.payments.model.Invoice, data: com.fasterxml.jackson.databind.JsonNode?) {
    if (invoice.status == InvoiceStatus.PAID || invoice.status == InvoiceStatus.REFUNDED) return

    val providerPaymentId = data?.get("provider_payment_id")?.asText()
    val amount = data?.get("amount_cents")?.asInt() ?: invoice.amountCents
    val currency = data?.get("currency")?.asText() ?: invoice.currency

    payments.create(invoice.id, providerPaymentId, amount, currency, PaymentStatus.SUCCEEDED)
    invoices.updateStatus(invoice.id, InvoiceStatus.PAID, now())
    subscriptions.updateStatus(
      invoice.subscriptionId,
      SubscriptionStatus.ACTIVE,
      invoice.periodStart,
      invoice.periodEnd,
      null
    )
    ledger.recordPayment("invoice:${invoice.id}", amount, currency)
  }

  private fun handlePaymentFailed(invoice: com.braincraft.payments.model.Invoice, data: com.fasterxml.jackson.databind.JsonNode?) {
    if (invoice.status == InvoiceStatus.PAID || invoice.status == InvoiceStatus.REFUNDED) return

    val providerPaymentId = data?.get("provider_payment_id")?.asText()
    val amount = data?.get("amount_cents")?.asInt() ?: invoice.amountCents
    val currency = data?.get("currency")?.asText() ?: invoice.currency

    payments.create(invoice.id, providerPaymentId, amount, currency, PaymentStatus.FAILED)
    invoices.updateStatus(invoice.id, InvoiceStatus.FAILED, null)
    subscriptions.updateStatus(invoice.subscriptionId, SubscriptionStatus.PAST_DUE, null, null, null)
  }

  private fun handleRefundSucceeded(invoice: com.braincraft.payments.model.Invoice, data: com.fasterxml.jackson.databind.JsonNode?) {
    if (invoice.status == InvoiceStatus.REFUNDED) return
    if (invoice.status != InvoiceStatus.PAID) return

    val amount = data?.get("amount_cents")?.asInt() ?: invoice.amountCents
    val currency = data?.get("currency")?.asText() ?: invoice.currency

    payments.create(invoice.id, data?.get("provider_payment_id")?.asText(), amount, currency, PaymentStatus.REFUNDED)
    invoices.updateStatus(invoice.id, InvoiceStatus.REFUNDED, invoice.paidAt)
    ledger.recordRefund("refund:${invoice.id}", amount, currency)
  }
}
