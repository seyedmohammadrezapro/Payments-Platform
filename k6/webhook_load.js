import http from "k6/http";
import crypto from "k6/crypto";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const WEBHOOK_SECRET = __ENV.WEBHOOK_SECRET || "change_me";
const INVOICE_ID = __ENV.INVOICE_ID || "00000000-0000-0000-0000-000000000000";

export const options = {
  vus: 10,
  duration: "30s",
  thresholds: {
    http_req_duration: ["p(95)<250"],
    http_req_failed: ["rate<0.01"]
  }
};

function sign(body) {
  return crypto.hmac("sha256", WEBHOOK_SECRET, body, "hex");
}

export default function () {
  const eventId = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    event_id: eventId,
    type: "payment_succeeded",
    created_at: new Date().toISOString(),
    data: {
      invoice_id: INVOICE_ID,
      provider_payment_id: eventId,
      amount_cents: 1000,
      currency: "USD"
    }
  });

  const headers = {
    "Content-Type": "application/json",
    "X-Signature": sign(payload)
  };

  const res = http.post(`${BASE_URL}/webhooks/provider`, payload, { headers });
  check(res, {
    "status is 200": (r) => r.status === 200
  });
  sleep(0.1);
}
