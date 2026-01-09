package com.braincraft.payments.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class MetricsService(private val registry: MeterRegistry) {
  private val receivedCounters = ConcurrentHashMap<String, Counter>()
  private val processedCounters = ConcurrentHashMap<String, Counter>()
  private val stageTimers = ConcurrentHashMap<String, Timer>()
  private val retriesCounter = Counter.builder("retries_total").register(registry)
  private val deadCounter = Counter.builder("dead_events_total").register(registry)
  private val ledgerCounter = Counter.builder("ledger_transactions_total").register(registry)

  fun recordEventReceived(type: String) {
    val counter = receivedCounters.computeIfAbsent(type) {
      Counter.builder("provider_events_received_total")
        .tag("type", type)
        .register(registry)
    }
    counter.increment()
  }

  fun recordEventProcessed(result: String) {
    val counter = processedCounters.computeIfAbsent(result) {
      Counter.builder("provider_events_processed_total")
        .tag("result", result)
        .register(registry)
    }
    counter.increment()
  }

  fun recordProcessing(stage: String, duration: Duration) {
    val timer = stageTimers.computeIfAbsent(stage) {
      Timer.builder("processing_duration_seconds")
        .tag("stage", stage)
        .publishPercentileHistogram()
        .register(registry)
    }
    timer.record(duration)
  }

  fun recordRetry() {
    retriesCounter.increment()
  }

  fun recordDeadEvent() {
    deadCounter.increment()
  }

  fun recordLedgerTransaction() {
    ledgerCounter.increment()
  }
}
