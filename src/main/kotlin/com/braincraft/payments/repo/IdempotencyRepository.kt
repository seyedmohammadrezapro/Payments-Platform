package com.braincraft.payments.repo

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class IdempotencyRepository(private val jdbc: NamedParameterJdbcTemplate) : IdempotencyStore {
  override fun find(key: String, scope: String): IdempotencyRecord? {
    val rows = jdbc.query(
      """
      SELECT key, scope, request_hash, response_json, created_at
      FROM idempotency_keys
      WHERE key = :key AND scope = :scope
      """.trimIndent(),
      mapOf("key" to key, "scope" to scope)
    ) { rs, _ ->
      IdempotencyRecord(
        key = rs.getString("key"),
        scope = rs.getString("scope"),
        requestHash = rs.getString("request_hash"),
        responseJson = rs.getString("response_json"),
        createdAt = rs.getTimestamp("created_at").toInstant()
      )
    }
    return rows.firstOrNull()
  }

  override fun save(key: String, scope: String, requestHash: String, responseJson: String) {
    jdbc.update(
      """
      INSERT INTO idempotency_keys (key, scope, request_hash, response_json)
      VALUES (:key, :scope, :request_hash, CAST(:response_json AS jsonb))
      ON CONFLICT (key, scope) DO NOTHING
      """.trimIndent(),
      mapOf(
        "key" to key,
        "scope" to scope,
        "request_hash" to requestHash,
        "response_json" to responseJson
      )
    )
  }
}
