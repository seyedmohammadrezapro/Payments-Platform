package com.braincraft.payments.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID


data class CreateCustomerRequest(
  @field:Email @field:NotBlank val email: String
)

data class CreatePlanRequest(
  @field:NotBlank val name: String,
  @field:Positive val amountCents: Int,
  @field:NotBlank val currency: String,
  @field:NotBlank val intervalUnit: String,
  @field:Positive val intervalCount: Int
)

data class CreateSubscriptionRequest(
  @field:NotNull val customerId: UUID,
  @field:NotNull val planId: UUID,
  val startAt: Instant?,
  val paymentMethodRef: String?
)

data class CancelSubscriptionRequest(
  val cancelAtPeriodEnd: Boolean?
)

// Webhook payload is parsed from raw JSON to preserve signature integrity.
data class ProviderWebhookRequest(
  @field:NotBlank val eventId: String,
  @field:NotBlank val type: String,
  @field:NotNull val createdAt: Instant,
  val data: Map<String, Any?>
)
