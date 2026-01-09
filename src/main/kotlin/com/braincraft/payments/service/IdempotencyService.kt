package com.braincraft.payments.service

import com.braincraft.payments.repo.IdempotencyStore
import com.braincraft.payments.util.HashUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class IdempotencyService(private val repo: IdempotencyStore) {
  fun findOrConflict(key: String, scope: String, requestHash: String): IdempotencyCheck {
    val existing = repo.find(key, scope) ?: return IdempotencyCheck(null, null)
    if (existing.requestHash != requestHash) {
      return IdempotencyCheck(null, HttpStatus.CONFLICT)
    }
    return IdempotencyCheck(existing.responseJson, null)
  }

  fun save(key: String, scope: String, requestHash: String, responseJson: String) {
    repo.save(key, scope, requestHash, responseJson)
  }

  fun hashRequest(bodyJson: String): String = HashUtils.sha256Hex(bodyJson)
}

data class IdempotencyCheck(
  val responseJson: String?,
  val errorStatus: HttpStatus?
)
