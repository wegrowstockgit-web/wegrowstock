import { createHmac } from 'node:crypto';
import { expect, test } from '@playwright/test';

const API_BASE = process.env.E2E_API_URL ?? 'http://localhost:8080';
const WEBHOOK_SECRET = process.env.STRIPE_WEBHOOK_SECRET ?? 'whsec_mock_secret';

function stripeSignature(rawBody: string, secret: string, timestampSeconds = Math.floor(Date.now() / 1000)): string {
  const digest = createHmac('sha256', secret).update(`${timestampSeconds}.${rawBody}`).digest('hex');
  return `t=${timestampSeconds},v1=${digest}`;
}

test.describe('Webhook idempotency', () => {
  test('concurrent duplicate events process exactly once', async ({ request }) => {
    const eventId = `evt_e2e_${Date.now()}`;
    const payload = {
      id: eventId,
      type: 'payment_intent.succeeded',
      tenant_id: 'a0000000-0000-4000-8000-000000000001',
      data: { id: 'pi_nonexistent_e2e' },
    };
    const rawBody = JSON.stringify(payload);

    const headers = {
      'Content-Type': 'application/json',
      'Stripe-Signature': stripeSignature(rawBody, WEBHOOK_SECRET),
    };

    const [res1, res2] = await Promise.all([
      request.post(`${API_BASE}/api/v1/webhooks/stripe`, { data: rawBody, headers }),
      request.post(`${API_BASE}/api/v1/webhooks/stripe`, { data: rawBody, headers }),
    ]);

    expect(res1.status(), `res1=${res1.status()}`).toBe(200);
    expect(res2.status(), `res2=${res2.status()}`).toBe(200);

    const body1 = await res1.json();
    const body2 = await res2.json();
    expect(body1.externalEventId).toBe(eventId);
    expect(body2.externalEventId).toBe(eventId);
    expect(body1.id).toBe(body2.id);
  });
});
