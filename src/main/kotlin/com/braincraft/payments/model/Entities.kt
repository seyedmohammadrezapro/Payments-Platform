package com.braincraft.payments.model

import java.time.Instant
import java.util.UUID


data class Customer(
  val id: UUID,
  val email: String,
  val createdAt: Instant
)

data class Plan(
  val id: UUID,
  val name: String,
  val amountCents: Int,
  val currency: String,
  val intervalUnit: String,
  val intervalCount: Int,
  val createdAt: Instant
)

data class Subscription(
  val id: UUID,
  val customerId: UUID,
  val planId: UUID,
  val status: SubscriptionStatus,
  val currentPeriodStart: Instant?,
  val currentPeriodEnd: Instant?,
  val cancelAtPeriodEnd: Boolean,
  val createdAt: Instant,
  val updatedAt: Instant
)

data class Invoice(
  val id: UUID,
  val subscriptionId: UUID,
  val amountCents: Int,
  val currency: String,
  val status: InvoiceStatus,
  val periodStart: Instant,
  val periodEnd: Instant,
  val createdAt: Instant,
  val updatedAt: Instant,
  val paidAt: Instant?
)

data class Payment(
  val id: UUID,
  val invoiceId: UUID,
  val providerPaymentId: String?,
  val amountCents: Int,
  val currency: String,
  val status: PaymentStatus,
  val createdAt: Instant
)

data class ProviderEvent(
  val id: UUID,
  val eventId: String,
  val type: String,
  val rawJson: String,
  val status: ProviderEventStatus,
  val receivedAt: Instant,
  val processedAt: Instant?,
  val attempts: Int,
  val lastError: String?
)

data class OutboxJob(
  val id: UUID,
  val aggregateType: String,
  val aggregateId: String,
  val type: String,
  val payloadJson: String,
  val status: OutboxStatus,
  val availableAt: Instant,
  val attempts: Int,
  val lastError: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)

data class LedgerAccount(
  val id: UUID,
  val code: String,
  val name: String,
  val type: LedgerAccountType
)

data class LedgerTransaction(
  val id: UUID,
  val externalRef: String?,
  val createdAt: Instant
)

data class LedgerEntry(
  val id: UUID,
  val transactionId: UUID,
  val accountId: UUID,
  val direction: LedgerDirection,
  val amountCents: Int,
  val currency: String,
  val createdAt: Instant
)
