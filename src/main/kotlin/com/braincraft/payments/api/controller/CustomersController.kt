package com.braincraft.payments.api.controller

import com.braincraft.payments.api.dto.CreateCustomerRequest
import com.braincraft.payments.api.dto.CustomerResponse
import com.braincraft.payments.api.dto.toResponse
import com.braincraft.payments.service.CustomerService
import com.braincraft.payments.service.IdempotencyService
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
@RequestMapping("/customers")
class CustomersController(
  private val customers: CustomerService,
  private val idempotency: IdempotencyService,
  private val objectMapper: ObjectMapper
) {
  @PostMapping
  fun create(
    @Valid @RequestBody req: CreateCustomerRequest,
    @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
  ): ResponseEntity<Any> {
    if (idempotencyKey.isNullOrBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "missing_idempotency_key"))
    }
    val scope = "POST /customers"
    val requestJson = objectMapper.writeValueAsString(req)
    val hash = idempotency.hashRequest(requestJson)
    val check = idempotency.findOrConflict(idempotencyKey, scope, hash)
    if (check.errorStatus != null) {
      return ResponseEntity.status(check.errorStatus).body(mapOf("error" to "idempotency_conflict"))
    }
    if (check.responseJson != null) {
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(check.responseJson)
    }

    val customer = customers.create(req.email).toResponse()
    val responseJson = objectMapper.writeValueAsString(customer)
    idempotency.save(idempotencyKey, scope, hash, responseJson)
    return ResponseEntity.status(HttpStatus.CREATED).body(customer)
  }
}
