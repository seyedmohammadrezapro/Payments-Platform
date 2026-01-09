package com.braincraft.payments.api.dto

import com.braincraft.payments.model.*
import java.time.Instant
import java.util.UUID


data class CustomerResponse(val id: UUID, val email: String, val createdAt: Instant)

fun Customer.toResponse() = CustomerResponse(id, email, createdAt)

data class PlanResponse(
  val id: UUID,
  val name: String,
  val amountCents: Int,
  val currency: String,
  val intervalUnit: String,
  val intervalCount: Int,
  val createdAt: Instant
)

fun Plan.toResponse() = PlanResponse(id, name, amountCents, currency, intervalUnit, intervalCount, createdAt)

data class SubscriptionResponse(
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

fun Subscription.toResponse() = SubscriptionResponse(
  id,
  customerId,
  planId,
  status,
  currentPeriodStart,
  currentPeriodEnd,
  cancelAtPeriodEnd,
  createdAt,
  updatedAt
)

data class InvoiceResponse(
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

fun Invoice.toResponse() = InvoiceResponse(
  id,
  subscriptionId,
  amountCents,
  currency,
  status,
  periodStart,
  periodEnd,
  createdAt,
  updatedAt,
  paidAt
)

data class PaymentResponse(
  val id: UUID,
  val invoiceId: UUID,
  val providerPaymentId: String?,
  val amountCents: Int,
  val currency: String,
  val status: PaymentStatus,
  val createdAt: Instant
)

fun Payment.toResponse() = PaymentResponse(id, invoiceId, providerPaymentId, amountCents, currency, status, createdAt)

data class SubscriptionCreateResponse(
  val subscription: SubscriptionResponse,
  val invoice: InvoiceResponse
)

data class CursorPage<T>(
  val items: List<T>,
  val nextCursor: String?
)
