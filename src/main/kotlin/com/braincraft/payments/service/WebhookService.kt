package com.braincraft.payments.service

import com.braincraft.payments.repo.OutboxRepository
import com.braincraft.payments.repo.ProviderEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookService(
  private val events: ProviderEventRepository,
  private val outbox: OutboxRepository,
  private val objectMapper: ObjectMapper
) {
  @Transactional
  fun ingest(eventId: String, type: String, rawJson: String, requestId: String?): Boolean {
    val inserted = events.insertIfAbsent(eventId, type, rawJson)
    if (!inserted) {
      return false
    }
    val payload = mutableMapOf<String, Any>("event_id" to eventId)
    if (requestId != null) payload["request_id"] = requestId
    outbox.enqueue("provider_event", eventId, "process_provider_event", objectMapper.writeValueAsString(payload))
    return true
  }
}
