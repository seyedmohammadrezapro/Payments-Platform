package com.braincraft.payments

import com.braincraft.payments.repo.*
import com.braincraft.payments.worker.OutboxWorker
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTestBase {
  @LocalServerPort
  var port: Int = 0

  @Autowired lateinit var rest: TestRestTemplate
  @Autowired lateinit var objectMapper: ObjectMapper
  @Autowired lateinit var outboxWorker: OutboxWorker
  @Autowired lateinit var jdbc: NamedParameterJdbcTemplate
  @Autowired lateinit var customers: CustomerRepository
  @Autowired lateinit var plans: PlanRepository
  @Autowired lateinit var subscriptions: SubscriptionRepository
  @Autowired lateinit var invoices: InvoiceRepository
  @Autowired lateinit var payments: PaymentRepository
  @Autowired lateinit var ledger: LedgerRepository

  protected fun url(path: String): String = "http://localhost:$port$path"

  @BeforeEach
  fun resetDb() {
    jdbc.update(
      "TRUNCATE TABLE ledger_entries, ledger_transactions, payments, invoices, subscriptions, plans, customers, provider_events, outbox_jobs, idempotency_keys RESTART IDENTITY CASCADE",
      emptyMap<String, Any>()
    )
  }

  companion object {
    @Container
    private val postgres = PostgreSQLContainer("postgres:16")

    @Container
    private val redis = GenericContainer<Nothing>("redis:7").withExposedPorts(6379)

    @JvmStatic
    @DynamicPropertySource
    fun register(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url") { postgres.jdbcUrl }
      registry.add("spring.datasource.username") { postgres.username }
      registry.add("spring.datasource.password") { postgres.password }
      registry.add("spring.redis.host") { redis.host }
      registry.add("spring.redis.port") { redis.getMappedPort(6379) }
      registry.add("app.adminApiKey") { "test_admin" }
      registry.add("app.webhookSecret") { "test_secret" }
      registry.add("app.eventMaxAttempts") { "2" }
      registry.add("app.rateLimitPerMinute") { "1000" }
      registry.add("spring.task.scheduling.enabled") { "false" }
    }
  }
}
