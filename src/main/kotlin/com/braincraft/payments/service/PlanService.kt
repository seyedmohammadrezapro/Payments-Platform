package com.braincraft.payments.service

import com.braincraft.payments.model.Plan
import com.braincraft.payments.repo.PlanRepository
import org.springframework.stereotype.Service

@Service
class PlanService(private val plans: PlanRepository) {
  fun create(name: String, amountCents: Int, currency: String, intervalUnit: String, intervalCount: Int): Plan {
    val unit = intervalUnit.lowercase()
    require(unit == "day" || unit == "month") { "interval_unit must be day or month" }
    val normalizedCurrency = currency.uppercase()
    return plans.create(name, amountCents, normalizedCurrency, unit, intervalCount)
  }
}
