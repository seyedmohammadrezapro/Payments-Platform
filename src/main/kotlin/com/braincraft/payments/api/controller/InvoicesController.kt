package com.braincraft.payments.api.controller

import com.braincraft.payments.api.dto.CursorPage
import com.braincraft.payments.api.dto.toResponse
import com.braincraft.payments.model.InvoiceStatus
import com.braincraft.payments.repo.InvoiceRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/invoices")
class InvoicesController(private val invoices: InvoiceRepository) {
  @GetMapping
  fun list(
    @RequestParam("subscription_id", required = false) subscriptionId: UUID?,
    @RequestParam("status", required = false) status: String?,
    @RequestParam("limit", required = false, defaultValue = "20") limit: Int,
    @RequestParam("cursor", required = false) cursor: String?
  ): ResponseEntity<Any> {
    val statusEnum = status?.let { InvoiceStatus.valueOf(it.uppercase()) }
    val cursorTime = cursor?.let { Instant.parse(it) }
    val items = invoices.listBySubscription(subscriptionId, statusEnum, limit, cursorTime)
    val nextCursor = items.lastOrNull()?.createdAt?.toString()
    return ResponseEntity.ok(CursorPage(items.map { it.toResponse() }, nextCursor))
  }

  @GetMapping("/{id}")
  fun get(@PathVariable id: String): ResponseEntity<Any> {
    val invoice = invoices.findById(UUID.fromString(id))
      ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))
    return ResponseEntity.ok(invoice.toResponse())
  }
}
