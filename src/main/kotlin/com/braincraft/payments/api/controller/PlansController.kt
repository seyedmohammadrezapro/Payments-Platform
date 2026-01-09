package com.braincraft.payments.api.controller

import com.braincraft.payments.api.dto.CreatePlanRequest
import com.braincraft.payments.api.dto.toResponse
import com.braincraft.payments.service.IdempotencyService
import com.braincraft.payments.service.PlanService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/plans")
class PlansController(
  private val plans: PlanService,
  private val idempotency: IdempotencyService,
  private val objectMapper: ObjectMapper
) {
  @PostMapping
  fun create(
    @Valid @RequestBody req: CreatePlanRequest,
    @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
  ): ResponseEntity<Any> {
    if (idempotencyKey.isNullOrBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "missing_idempotency_key"))
    }
    val scope = "POST /plans"
    val requestJson = objectMapper.writeValueAsString(req)
    val hash = idempotency.hashRequest(requestJson)
    val check = idempotency.findOrConflict(idempotencyKey, scope, hash)
    if (check.errorStatus != null) {
      return ResponseEntity.status(check.errorStatus).body(mapOf("error" to "idempotency_conflict"))
    }
    if (check.responseJson != null) {
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(check.responseJson)
    }

    val plan = plans.create(req.name, req.amountCents, req.currency, req.intervalUnit, req.intervalCount).toResponse()
    val responseJson = objectMapper.writeValueAsString(plan)
    idempotency.save(idempotencyKey, scope, hash, responseJson)
    return ResponseEntity.status(HttpStatus.CREATED).body(plan)
  }
}
