#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
WEBHOOK_SECRET=${WEBHOOK_SECRET:-change_me}
EVENT_TYPE=${1:-payment_succeeded}
INVOICE_ID=${2:-}

if [[ -z "${INVOICE_ID}" ]]; then
  echo "Usage: $0 <payment_succeeded|payment_failed|refund_succeeded> <invoice_id>" >&2
  exit 1
fi

if command -v uuidgen >/dev/null 2>&1; then
  EVENT_ID=$(uuidgen)
else
  EVENT_ID=$(cat /proc/sys/kernel/random/uuid)
fi

NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

PAYLOAD=$(cat <<JSON
{
  "event_id": "${EVENT_ID}",
  "type": "${EVENT_TYPE}",
  "created_at": "${NOW}",
  "data": {
    "invoice_id": "${INVOICE_ID}",
    "provider_payment_id": "pay_${EVENT_ID}",
    "amount_cents": 1000,
    "currency": "USD"
  }
}
JSON
)

SIGNATURE=$(printf "%s" "${PAYLOAD}" | openssl dgst -sha256 -hmac "${WEBHOOK_SECRET}" -binary | xxd -p -c 256)

curl -sS -X POST "${BASE_URL}/webhooks/provider" \
  -H "Content-Type: application/json" \
  -H "X-Signature: ${SIGNATURE}" \
  -d "${PAYLOAD}"

echo ""
