package com.braincraft.payments.service

import com.braincraft.payments.model.Customer
import com.braincraft.payments.repo.CustomerRepository
import org.springframework.stereotype.Service

@Service
class CustomerService(private val customers: CustomerRepository) {
  fun create(email: String): Customer = customers.create(email)
}
