package com.braincraft.payments.api.controller

import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class HealthController(
  private val jdbc: NamedParameterJdbcTemplate,
  private val redisProvider: ObjectProvider<StringRedisTemplate>
) {
  @GetMapping("healthz")
  fun health(): ResponseEntity<Any> = ResponseEntity.ok(mapOf("status" to "ok"))

  @GetMapping("readyz")
  fun ready(): ResponseEntity<Any> {
    val checks = mutableMapOf<String, Any>()
    var healthy = true

    try {
      jdbc.queryForObject("SELECT 1", emptyMap<String, Any>(), Int::class.java)
      checks["db"] = "ok"
    } catch (ex: Exception) {
      checks["db"] = "fail"
      healthy = false
    }

    val redis = redisProvider.ifAvailable
    if (redis == null) {
      checks["redis"] = "disabled"
    } else {
      try {
        val pong = redis.connectionFactory?.connection?.ping()
        checks["redis"] = pong ?: "no_response"
        if (pong == null) healthy = false
      } catch (ex: Exception) {
        checks["redis"] = "fail"
        healthy = false
      }
    }

    return if (healthy) {
      ResponseEntity.ok(mapOf("status" to "ready", "checks" to checks))
    } else {
      ResponseEntity.status(503).body(mapOf("status" to "not_ready", "checks" to checks))
    }
  }
}
