package com.braincraft.payments.model

enum class SubscriptionStatus { PENDING, ACTIVE, PAST_DUE, CANCELLED }

enum class InvoiceStatus { PENDING, PAID, FAILED, REFUNDED }

enum class PaymentStatus { SUCCEEDED, FAILED, REFUNDED }

enum class ProviderEventStatus { RECEIVED, PROCESSING, SUCCEEDED, FAILED, DEAD }

enum class OutboxStatus { PENDING, PROCESSING, SUCCEEDED, FAILED, DEAD }

enum class LedgerDirection { DEBIT, CREDIT }

enum class LedgerAccountType { ASSET, INCOME, EXPENSE, CONTRA_REVENUE }
