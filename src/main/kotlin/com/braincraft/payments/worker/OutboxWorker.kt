package com.braincraft.payments.worker

import com.braincraft.payments.config.AppProperties
import com.braincraft.payments.metrics.MetricsService
import com.braincraft.payments.model.ProviderEventStatus
import com.braincraft.payments.repo.OutboxRepository
import com.braincraft.payments.repo.ProviderEventRepository
import com.braincraft.payments.service.ProviderEventProcessor
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class OutboxWorker(
  private val outbox: OutboxRepository,
  private val providerEvents: ProviderEventRepository,
  private val processor: ProviderEventProcessor,
  private val objectMapper: ObjectMapper,
  private val props: AppProperties,
  private val metrics: MetricsService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun processBatch(limit: Int): Int {
    val jobs = outbox.claimBatch(limit)
    if (jobs.isEmpty()) return 0

    jobs.forEach { job ->
      val payload = objectMapper.readTree(job.payloadJson)
      val eventId = payload.get("event_id").asText()
      val requestId = payload.get("request_id")?.asText()
      if (requestId != null) MDC.put("request_id", requestId)
      MDC.put("event_id", eventId)

      val started = Instant.now()
      try {
        processor.process(eventId)
        outbox.markSucceeded(job.id)
        providerEvents.updateStatus(eventId, ProviderEventStatus.SUCCEEDED, job.attempts, null, processor.now())
        metrics.recordEventProcessed("succeeded")
      } catch (ex: Exception) {
        val nextAttempts = job.attempts + 1
        val maxAttempts = props.eventMaxAttempts
        if (nextAttempts >= maxAttempts) {
          outbox.markDead(job.id, nextAttempts, ex.message ?: "processing failed")
          providerEvents.updateStatus(eventId, ProviderEventStatus.DEAD, nextAttempts, ex.message, processor.now())
          metrics.recordEventProcessed("dead")
          metrics.recordDeadEvent()
          log.error("event processing dead", ex)
        } else {
          val nextTime = processor.nextRetryAt(nextAttempts)
          outbox.markFailed(job.id, nextAttempts, ex.message ?: "processing failed", nextTime)
          providerEvents.updateStatus(eventId, ProviderEventStatus.FAILED, nextAttempts, ex.message, null)
          metrics.recordEventProcessed("failed")
          metrics.recordRetry()
          log.warn("event processing failed, will retry", ex)
        }
      } finally {
        metrics.recordProcessing("event_process", Duration.between(started, Instant.now()))
        MDC.remove("event_id")
        MDC.remove("request_id")
      }
    }

    return jobs.size
  }
}
