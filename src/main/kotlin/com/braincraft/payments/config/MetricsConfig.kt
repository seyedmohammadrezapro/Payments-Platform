package com.braincraft.payments.config

import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.repo.InvoiceRepository
import com.braincraft.payments.repo.OutboxRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig(
  private val registry: MeterRegistry,
  private val invoices: InvoiceRepository,
  private val outbox: OutboxRepository
) {
  @PostConstruct
  fun registerGauges() {
    InvoiceStatus.values().forEach { status ->
      Gauge.builder("invoices_total") { invoices.countByStatus(status).toDouble() }
        .tag("status", status.name.lowercase())
        .register(registry)
    }

    Gauge.builder("queue_depth") { outbox.countPending().toDouble() }
      .register(registry)
  }
}
