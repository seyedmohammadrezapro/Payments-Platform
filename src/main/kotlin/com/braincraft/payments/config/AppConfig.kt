package com.braincraft.payments.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppConfig {
  @Bean
  fun objectMapper(): ObjectMapper {
    return ObjectMapper()
      .registerModule(KotlinModule.Builder().build())
      .registerModule(JavaTimeModule())
  }

  @Bean
  fun clock(): Clock = Clock.systemUTC()
}
