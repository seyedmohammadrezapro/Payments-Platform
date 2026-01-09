package com.braincraft.payments.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

object TimeUtils {
  fun addInterval(start: Instant, unit: String, count: Int): Instant {
    val zdt = ZonedDateTime.ofInstant(start, ZoneOffset.UTC)
    return when (unit) {
      "day" -> zdt.plusDays(count.toLong()).toInstant()
      "month" -> zdt.plusMonths(count.toLong()).toInstant()
      else -> throw IllegalArgumentException("Unsupported interval unit: $unit")
    }
  }
}
