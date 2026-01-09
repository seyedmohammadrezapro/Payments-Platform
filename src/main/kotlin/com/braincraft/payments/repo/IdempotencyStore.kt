package com.braincraft.payments.repo

import java.time.Instant

interface IdempotencyStore {
  fun find(key: String, scope: String): IdempotencyRecord?
  fun save(key: String, scope: String, requestHash: String, responseJson: String)
}

data class IdempotencyRecord(
  val key: String,
  val scope: String,
  val requestHash: String,
  val responseJson: String,
  val createdAt: Instant
)
