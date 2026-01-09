#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
ADMIN_KEY=${ADMIN_KEY:-change_me}
WEBHOOK_SECRET=${WEBHOOK_SECRET:-change_me}

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1" >&2; exit 1; }
}

require jq
require openssl

post_idempotent() {
  local path="$1"
  local body="$2"
  local key="$3"
  curl -sS -X POST "${BASE_URL}${path}" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: ${key}" \
    -d "${body}"
}

get_admin() {
  local path="$1"
  curl -sS -H "X-Admin-Key: ${ADMIN_KEY}" "${BASE_URL}${path}"
}

echo "Creating customer..."
CUSTOMER=$(post_idempotent "/customers" '{"email":"demo@example.com"}' "cust-demo")
CUSTOMER_ID=$(echo "${CUSTOMER}" | jq -r '.id')

echo "Creating plan..."
PLAN=$(post_idempotent "/plans" '{"name":"Starter","amountCents":1000,"currency":"USD","intervalUnit":"day","intervalCount":1}' "plan-demo")
PLAN_ID=$(echo "${PLAN}" | jq -r '.id')

echo "Creating subscription..."
SUB=$(post_idempotent "/subscriptions" "{\"customerId\":\"${CUSTOMER_ID}\",\"planId\":\"${PLAN_ID}\"}" "sub-demo")
INVOICE_ID=$(echo "${SUB}" | jq -r '.invoice.id')
SUB_ID=$(echo "${SUB}" | jq -r '.subscription.id')

echo "Sending payment_succeeded webhook..."
EVENT_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
PAYLOAD=$(cat <<JSON
{
  "event_id": "${EVENT_ID}",
  "type": "payment_succeeded",
  "created_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "data": {
    "invoice_id": "${INVOICE_ID}",
    "provider_payment_id": "pay_${EVENT_ID}",
    "amount_cents": 1000,
    "currency": "USD"
  }
}
JSON
)
SIG=$(printf "%s" "${PAYLOAD}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" -binary | xxd -p -c 256)

curl -sS -X POST "${BASE_URL}/webhooks/provider" \
  -H "Content-Type: application/json" \
  -H "X-Signature: ${SIG}" \
  -d "${PAYLOAD}" >/dev/null

for i in {1..10}; do
  STATUS=$(curl -sS "${BASE_URL}/invoices/${INVOICE_ID}" | jq -r '.status')
  if [[ "${STATUS}" == "paid" ]]; then
    echo "Invoice paid."
    break
  fi
  sleep 1
done

echo "Sending duplicate webhook (idempotency check)..."
curl -sS -X POST "${BASE_URL}/webhooks/provider" \
  -H "Content-Type: application/json" \
  -H "X-Signature: ${SIG}" \
  -d "${PAYLOAD}" | jq -r '.duplicate'

echo "Sending invalid webhook to trigger dead-letter..."
BAD_EVENT_ID=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
BAD_PAYLOAD=$(cat <<JSON
{
  "event_id": "${BAD_EVENT_ID}",
  "type": "payment_succeeded",
  "created_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "data": {
    "invoice_id": "00000000-0000-0000-0000-000000000000",
    "provider_payment_id": "pay_${BAD_EVENT_ID}",
    "amount_cents": 1000,
    "currency": "USD"
  }
}
JSON
)
BAD_SIG=$(printf "%s" "${BAD_PAYLOAD}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" -binary | xxd -p -c 256)

curl -sS -X POST "${BASE_URL}/webhooks/provider" \
  -H "Content-Type: application/json" \
  -H "X-Signature: ${BAD_SIG}" \
  -d "${BAD_PAYLOAD}" >/dev/null

for i in {1..12}; do
  DEAD_COUNT=$(get_admin "/admin/events?status=dead&limit=20" | jq '.items | length')
  if [[ "${DEAD_COUNT}" -gt 0 ]]; then
    echo "Dead-letter event recorded."
    break
  fi
  sleep 2
done

echo "Done."
