package com.braincraft.payments

import com.braincraft.payments.config.AppProperties
import com.braincraft.payments.ledger.LedgerValidator
import com.braincraft.payments.model.LedgerDirection
import com.braincraft.payments.repo.IdempotencyRecord
import com.braincraft.payments.repo.IdempotencyStore
import com.braincraft.payments.repo.LedgerEntryInsert
import com.braincraft.payments.service.IdempotencyService
import com.braincraft.payments.service.SignatureService
import com.braincraft.payments.util.HashUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class LedgerValidatorTest {
  private val validator = LedgerValidator()

  @Test
  fun ledgerEntriesMustBalance() {
    val entries = listOf(
      LedgerEntryInsert(java.util.UUID.randomUUID(), LedgerDirection.DEBIT, 1000, "USD"),
      LedgerEntryInsert(java.util.UUID.randomUUID(), LedgerDirection.CREDIT, 1000, "USD")
    )
    validator.ensureBalanced(entries)
  }

  @Test
  fun ledgerEntriesRejectImbalance() {
    val entries = listOf(
      LedgerEntryInsert(java.util.UUID.randomUUID(), LedgerDirection.DEBIT, 900, "USD"),
      LedgerEntryInsert(java.util.UUID.randomUUID(), LedgerDirection.CREDIT, 1000, "USD")
    )
    assertThatThrownBy { validator.ensureBalanced(entries) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }
}

class SignatureServiceTest {
  @Test
  fun signatureVerificationMatchesHmac() {
    val props = AppProperties(webhookSecret = "secret")
    val service = SignatureService(props)
    val body = "{\"event_id\":\"1\"}".toByteArray()
    val signature = HashUtils.hmacSha256Hex("secret", body)
    assertThat(service.verifySignature(body, signature)).isTrue()
    assertThat(service.verifySignature(body, "bad")).isFalse()
  }
}

class IdempotencyServiceTest {
  private val store = InMemoryIdempotencyStore()
  private val service = IdempotencyService(store)

  @Test
  fun idempotencyReturnsCachedResponse() {
    val key = "key-1"
    val scope = "POST /customers"
    val hash = service.hashRequest("{\"email\":\"a@b.com\"}")
    val response = "{\"id\":\"123\"}"

    val first = service.findOrConflict(key, scope, hash)
    assertThat(first.responseJson).isNull()

    service.save(key, scope, hash, response)

    val second = service.findOrConflict(key, scope, hash)
    assertThat(second.responseJson).isEqualTo(response)
  }

  @Test
  fun idempotencyDetectsConflicts() {
    val key = "key-2"
    val scope = "POST /plans"
    val hash = service.hashRequest("{\"name\":\"Plan\"}")
    service.save(key, scope, hash, "{\"id\":\"p1\"}")

    val conflict = service.findOrConflict(key, scope, "different")
    assertThat(conflict.errorStatus).isNotNull()
  }

  private class InMemoryIdempotencyStore : IdempotencyStore {
    private val storage = ConcurrentHashMap<String, IdempotencyRecord>()

    override fun find(key: String, scope: String): IdempotencyRecord? {
      return storage["$scope:$key"]
    }

    override fun save(key: String, scope: String, requestHash: String, responseJson: String) {
      storage.putIfAbsent(
        "$scope:$key",
        IdempotencyRecord(key, scope, requestHash, responseJson, Instant.now())
      )
    }
  }
}
