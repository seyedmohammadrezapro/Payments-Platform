package com.braincraft.payments.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
  var role: String = "api",
  var env: String = "dev",
  var adminApiKey: String = "change_me",
  var webhookSecret: String = "change_me",
  var eventMaxAttempts: Int = 5,
  var outboxBatchSize: Int = 50,
  var outboxPollIntervalMs: Long = 1000,
  var schedulerIntervalMs: Long = 60000,
  var rateLimitPerMinute: Long = 120,
  var idempotencyTtlMinutes: Long = 1440,
  var processingTimeoutMinutes: Long = 15,
  var maxPayloadBytes: Long = 65536
)
