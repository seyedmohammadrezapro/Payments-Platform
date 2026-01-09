package com.braincraft.payments.repo

import com.braincraft.payments.model.Customer
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class CustomerRepository(private val jdbc: NamedParameterJdbcTemplate) {
  private val mapper = RowMapper { rs: ResultSet, _: Int ->
    Customer(
      id = rs.getObject("id", UUID::class.java),
      email = rs.getString("email"),
      createdAt = rs.getTimestamp("created_at").toInstant()
    )
  }

  fun create(email: String): Customer {
    val id = jdbc.queryForObject(
      "INSERT INTO customers (email) VALUES (:email) RETURNING id",
      mapOf("email" to email),
      UUID::class.java
    )
    return findById(id!!)!!
  }

  fun findById(id: UUID): Customer? {
    val rows = jdbc.query(
      "SELECT * FROM customers WHERE id = :id",
      mapOf("id" to id),
      mapper
    )
    return rows.firstOrNull()
  }
}
