package com.braincraft.payments.worker

import com.braincraft.payments.config.AppProperties
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("worker")
class OutboxProcessor(
  private val worker: OutboxWorker,
  private val props: AppProperties
) {
  @Scheduled(fixedDelayString = "\${app.outboxPollIntervalMs}")
  fun poll() {
    worker.processBatch(props.outboxBatchSize)
  }
}
