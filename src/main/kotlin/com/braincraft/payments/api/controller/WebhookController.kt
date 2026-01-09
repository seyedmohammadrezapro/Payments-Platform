package com.braincraft.payments.api.controller

import com.braincraft.payments.metrics.MetricsService
import com.braincraft.payments.service.SignatureService
import com.braincraft.payments.service.WebhookService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/webhooks")
class WebhookController(
  private val signature: SignatureService,
  private val webhooks: WebhookService,
  private val objectMapper: ObjectMapper,
  private val metrics: MetricsService
) {
  @PostMapping("/provider")
  fun ingest(request: HttpServletRequest): ResponseEntity<Any> {
    val start = Instant.now()
    val raw = request.inputStream.readBytes()
    val sig = request.getHeader("X-Signature")
    if (!signature.verifySignature(raw, sig)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "invalid_signature"))
    }
    val json = String(raw, Charsets.UTF_8)
    val root = objectMapper.readTree(json)
    val eventId = root.get("event_id")?.asText()
    val type = root.get("type")?.asText()
    if (eventId.isNullOrBlank() || type.isNullOrBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "invalid_payload"))
    }

    val requestId = request.getHeader("X-Request-Id")
    val inserted = webhooks.ingest(eventId, type, json, requestId)
    metrics.recordEventReceived(type)
    metrics.recordProcessing("webhook_ingest", Duration.between(start, Instant.now()))
    return ResponseEntity.ok(mapOf("received" to true, "duplicate" to !inserted))
  }
}
