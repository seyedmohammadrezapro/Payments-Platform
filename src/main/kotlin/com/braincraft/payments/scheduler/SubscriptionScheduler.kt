package com.braincraft.payments.scheduler

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("scheduler")
class SubscriptionScheduler(private val renewalService: RenewalService) {

  @Scheduled(fixedDelayString = "\${app.schedulerIntervalMs}")
  fun run() {
    renewalService.runOnce(100)
  }
}
