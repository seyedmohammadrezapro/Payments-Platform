package com.braincraft.payments.api.controller

import com.braincraft.payments.api.dto.CursorPage
import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.model.ProviderEventStatus
import com.braincraft.payments.model.SubscriptionStatus
import com.braincraft.payments.repo.InvoiceRepository
import com.braincraft.payments.repo.LedgerRepository
import com.braincraft.payments.repo.ProviderEventRepository
import com.braincraft.payments.repo.SubscriptionRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/admin")
class AdminController(
  private val events: ProviderEventRepository,
  private val invoices: InvoiceRepository,
  private val subscriptions: SubscriptionRepository,
  private val ledger: LedgerRepository
) {
  @GetMapping("/stats")
  fun stats(): ResponseEntity<Any> {
    val invoicesByStatus = mapOf(
      "pending" to invoices.countByStatus(InvoiceStatus.PENDING),
      "paid" to invoices.countByStatus(InvoiceStatus.PAID),
      "failed" to invoices.countByStatus(InvoiceStatus.FAILED),
      "refunded" to invoices.countByStatus(InvoiceStatus.REFUNDED)
    )

    val subscriptionsByStatus = mapOf(
      "pending" to subscriptions.countByStatus(SubscriptionStatus.PENDING),
      "active" to subscriptions.countByStatus(SubscriptionStatus.ACTIVE),
      "past_due" to subscriptions.countByStatus(SubscriptionStatus.PAST_DUE),
      "cancelled" to subscriptions.countByStatus(SubscriptionStatus.CANCELLED)
    )

    val totalEvents = events.countAll()
    val retryRate = if (totalEvents > 0) {
      events.countWithAttempts(1).toDouble() / totalEvents.toDouble()
    } else {
      0.0
    }

    val eventsByStatus = mapOf(
      "received" to events.countByStatus(ProviderEventStatus.RECEIVED),
      "processing" to events.countByStatus(ProviderEventStatus.PROCESSING),
      "succeeded" to events.countByStatus(ProviderEventStatus.SUCCEEDED),
      "failed" to events.countByStatus(ProviderEventStatus.FAILED),
      "dead" to events.countByStatus(ProviderEventStatus.DEAD)
    )

    return ResponseEntity.ok(
      mapOf(
        "counts" to mapOf(
          "events_received" to totalEvents,
          "events_processed" to eventsByStatus["succeeded"],
          "events_failed" to eventsByStatus["failed"],
          "events_dead" to eventsByStatus["dead"]
        ),
        "events" to eventsByStatus,
        "invoices" to invoicesByStatus,
        "subscriptions" to subscriptionsByStatus,
        "processing" to mapOf(
          "retry_rate" to retryRate,
          "p95_process_duration_seconds" to events.processingP95Seconds()
        )
      )
    )
  }

  @GetMapping("/events")
  fun listEvents(
    @RequestParam("status", required = false) status: String?,
    @RequestParam("limit", required = false, defaultValue = "20") limit: Int,
    @RequestParam("cursor", required = false) cursor: String?
  ): ResponseEntity<Any> {
    val statusEnum = status?.let { ProviderEventStatus.valueOf(it.uppercase()) }
    val cursorTime = cursor?.let { Instant.parse(it) }
    val items = events.list(statusEnum, limit, cursorTime)
    val nextCursor = items.lastOrNull()?.receivedAt?.toString()
    return ResponseEntity.ok(CursorPage(items, nextCursor))
  }

  @GetMapping("/ledger/transactions")
  fun listLedger(@RequestParam("limit", required = false, defaultValue = "20") limit: Int,
                 @RequestParam("cursor", required = false) cursor: String?): ResponseEntity<Any> {
    val cursorTime = cursor?.let { Instant.parse(it) }
    val items = ledger.listTransactions(limit, cursorTime)
    val nextCursor = items.lastOrNull()?.createdAt?.toString()
    return ResponseEntity.ok(CursorPage(items, nextCursor))
  }

  @GetMapping("/ledger/transactions/{id}")
  fun getLedger(@PathVariable id: String): ResponseEntity<Any> {
    val tx = ledger.getTransaction(UUID.fromString(id)) ?: return ResponseEntity.notFound().build()
    val entries = ledger.listEntries(tx.id)
    return ResponseEntity.ok(mapOf("transaction" to tx, "entries" to entries))
  }
}
