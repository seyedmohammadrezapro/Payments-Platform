package com.braincraft.payments.api.controller

import com.braincraft.payments.api.dto.*
import com.braincraft.payments.service.IdempotencyService
import com.braincraft.payments.service.SubscriptionService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/subscriptions")
class SubscriptionsController(
  private val subscriptions: SubscriptionService,
  private val idempotency: IdempotencyService,
  private val objectMapper: ObjectMapper
) {
  @PostMapping
  fun create(
    @Valid @RequestBody req: CreateSubscriptionRequest,
    @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
  ): ResponseEntity<Any> {
    if (idempotencyKey.isNullOrBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "missing_idempotency_key"))
    }
    val scope = "POST /subscriptions"
    val requestJson = objectMapper.writeValueAsString(req)
    val hash = idempotency.hashRequest(requestJson)
    val check = idempotency.findOrConflict(idempotencyKey, scope, hash)
    if (check.errorStatus != null) {
      return ResponseEntity.status(check.errorStatus).body(mapOf("error" to "idempotency_conflict"))
    }
    if (check.responseJson != null) {
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(check.responseJson)
    }

    val result = subscriptions.createSubscription(req.customerId, req.planId, req.startAt)
    val response = SubscriptionCreateResponse(result.subscription.toResponse(), result.invoice.toResponse())
    val responseJson = objectMapper.writeValueAsString(response)
    idempotency.save(idempotencyKey, scope, hash, responseJson)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @PostMapping("/{id}/cancel")
  fun cancel(@PathVariable id: String, @RequestBody req: CancelSubscriptionRequest?): ResponseEntity<Any> {
    val cancelAtPeriodEnd = req?.cancelAtPeriodEnd ?: true
    subscriptions.cancelSubscription(java.util.UUID.fromString(id), cancelAtPeriodEnd)
    return ResponseEntity.ok(mapOf("status" to "cancelled"))
  }

  @GetMapping("/{id}")
  fun get(@PathVariable id: String): ResponseEntity<Any> {
    val sub = subscriptions.findById(java.util.UUID.fromString(id))
      ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))
    return ResponseEntity.ok(sub.toResponse())
  }
}
