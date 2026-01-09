# Architecture

## Components
- **API Service**: REST endpoints for customers, plans, subscriptions, invoices, and admin stats.
- **Webhook Ingest**: Validates signatures, stores raw provider events, and enqueues outbox jobs.
- **Outbox Processor (Worker)**: Pulls jobs, processes provider events, applies state changes, writes ledger entries, and retries on failure.
- **Scheduler**: Periodically generates renewal invoices with period uniqueness enforcement.
- **PostgreSQL**: Source of truth for all state, idempotency, ledger, and outbox jobs.
- **Redis**: Optional rate limiting and lightweight locks.
- **Observability**: JSON logs + Prometheus metrics + health/readiness endpoints.

## Data Model (ER)
```mermaid
erDiagram
  CUSTOMERS ||--o{ SUBSCRIPTIONS : has
  PLANS ||--o{ SUBSCRIPTIONS : provides
  SUBSCRIPTIONS ||--o{ INVOICES : bills
  INVOICES ||--o{ PAYMENTS : settles
  PROVIDER_EVENTS ||--o{ OUTBOX_JOBS : enqueues
  LEDGER_TRANSACTIONS ||--o{ LEDGER_ENTRIES : contains
  LEDGER_ACCOUNTS ||--o{ LEDGER_ENTRIES : posts

  CUSTOMERS {
    uuid id PK
    string email
    timestamp created_at
  }
  PLANS {
    uuid id PK
    string name
    int amount_cents
    string currency
    string interval_unit
    int interval_count
    timestamp created_at
  }
  SUBSCRIPTIONS {
    uuid id PK
    uuid customer_id FK
    uuid plan_id FK
    string status
    timestamp current_period_start
    timestamp current_period_end
    boolean cancel_at_period_end
    timestamp created_at
    timestamp updated_at
  }
  INVOICES {
    uuid id PK
    uuid subscription_id FK
    int amount_cents
    string currency
    string status
    timestamp period_start
    timestamp period_end
    timestamp paid_at
    timestamp created_at
    timestamp updated_at
  }
  PAYMENTS {
    uuid id PK
    uuid invoice_id FK
    string provider_payment_id
    int amount_cents
    string currency
    string status
    timestamp created_at
  }
  PROVIDER_EVENTS {
    uuid id PK
    string event_id UK
    string type
    json raw_json
    string status
    int attempts
    timestamp received_at
    timestamp processed_at
    string last_error
  }
  OUTBOX_JOBS {
    uuid id PK
    string aggregate_type
    string aggregate_id
    string type
    json payload_json
    string status
    timestamp available_at
    int attempts
    string last_error
  }
  IDEMPOTENCY_KEYS {
    string key UK
    string scope
    string request_hash
    json response_json
    timestamp created_at
  }
  LEDGER_ACCOUNTS {
    uuid id PK
    string code UK
    string name
    string type
  }
  LEDGER_TRANSACTIONS {
    uuid id PK
    string external_ref
    timestamp created_at
  }
  LEDGER_ENTRIES {
    uuid id PK
    uuid transaction_id FK
    uuid account_id FK
    string direction
    int amount_cents
    string currency
  }
```

## State Machines

### Provider Events
```mermaid
stateDiagram-v2
  [*] --> RECEIVED
  RECEIVED --> PROCESSING
  PROCESSING --> SUCCEEDED
  PROCESSING --> FAILED
  FAILED --> PROCESSING : retry
  FAILED --> DEAD : max_attempts
  SUCCEEDED --> [*]
  DEAD --> [*]
```

### Invoices
```mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> PAID
  PENDING --> FAILED
  FAILED --> PAID
  PAID --> REFUNDED
  REFUNDED --> [*]
```

### Subscriptions
```mermaid
stateDiagram-v2
  [*] --> PENDING
  PENDING --> ACTIVE
  ACTIVE --> PAST_DUE
  PAST_DUE --> ACTIVE
  ACTIVE --> CANCELLED
  PAST_DUE --> CANCELLED
  CANCELLED --> [*]
```

## Idempotency Strategy
- **Client requests**: `Idempotency-Key` header required for create endpoints. Stored in `idempotency_keys` with `scope` and a request hash. Duplicate key returns the stored response. Same key with different payload returns 409.
- **Webhook events**: `provider_events.event_id` is unique. Duplicate events return 200 without side effects.
- **Invoice periods**: `UNIQUE (subscription_id, period_start, period_end)` prevents double billing.

## Outbox Choice and Justification
- **Transactional Outbox** in Postgres ensures the event record and outbox job are committed atomically.
- Webhook ingest never loses events even if the worker is down.
- Worker retries safely with `SELECT ... FOR UPDATE SKIP LOCKED` and backoff.

## Retry and DLQ Strategy
- Exponential backoff with jitter: `delay = base * 2^attempt + random(0, jitter)`.
- Attempts tracked on both `provider_events` and `outbox_jobs`.
- After `max_attempts`, mark event and outbox job as `dead` and expose in admin endpoints.

## Ledger Guarantees
- Ledger transactions are append-only.
- Every transaction has at least two entries.
- `sum(debit) == sum(credit)` is enforced in code and tested.
- All amounts stored in minor units (cents) with explicit currency.
