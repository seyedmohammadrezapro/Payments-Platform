package com.braincraft.payments.service

import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class RateLimiterService(
  private val redisProvider: ObjectProvider<StringRedisTemplate>
) {
  private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC)

  fun allow(key: String, limitPerMinute: Long): Boolean {
    val redis = redisProvider.ifAvailable ?: return true
    val minuteKey = formatter.format(Instant.now())
    val fullKey = "rate:$key:$minuteKey"
    val count = redis.opsForValue().increment(fullKey) ?: 0
    if (count == 1L) {
      redis.expire(fullKey, java.time.Duration.ofSeconds(70))
    }
    return count <= limitPerMinute
  }
}
