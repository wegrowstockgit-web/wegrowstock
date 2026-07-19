import { createHmac } from 'node:crypto';
import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, WIDGET_S_SKU } from './helpers';

const DEMO_EDI_PARTNER_ID = 'a0000000-0000-4000-8000-000000003401';

/**
 * Journey 43 — Unified inbound CDM edge: Shopify JSON + EDI X12 850 → sales orders.
 */
test.describe('Journey 43: Inbound canonical orders', () => {
  test.setTimeout(180_000);

  test('SHOPIFY and EDI adapters create confirmed sales orders', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const stamp = Date.now().toString(36);
      const webhookSecret = `e2e-shopify-${stamp}`;

      const channelRes = await owner.page.request.put('/api/v1/integrations/hub/channels/SHOPIFY', {
        data: {
          channelType: 'SHOPIFY',
          status: 'ACTIVE',
          credentials: { webhookSecret, accessToken: 'e2e-token' },
          settings: { shopDomain: `e2e-${stamp}.myshopify.com` },
        },
      });
      expect(channelRes.ok(), await channelRes.text()).toBeTruthy();

      const shopifyPayload = {
        topic: 'orders/create',
        name: `#E2E-${stamp}`,
        email: 'owner@demo.test',
        billing_address: {
          name: 'E2E Bill',
          address1: '100 Test Ave',
          city: 'Austin',
          province: 'TX',
          zip: '78701',
          country: 'US',
        },
        shipping_address: {
          name: 'E2E Ship',
          address1: '200 Dock Rd',
          city: 'Dallas',
          province: 'TX',
          zip: '75201',
          country: 'US',
        },
        line_items: [{ sku: WIDGET_S_SKU, quantity: 2, price: '19.99' }],
      };
      const rawBody = JSON.stringify(shopifyPayload);
      const hmac = createHmac('sha256', webhookSecret).update(rawBody, 'utf8').digest('base64');

      const shopifyRes = await owner.page.request.post('/api/v1/integrations/inbound/SHOPIFY', {
        data: rawBody,
        headers: {
          'Content-Type': 'application/json',
          'X-Shopify-Hmac-Sha256': hmac,
        },
      });
      expect(shopifyRes.ok(), await shopifyRes.text()).toBeTruthy();
      const shopifyOrder = (await shopifyRes.json()) as {
        id: string;
        number: string;
        status: string;
        channel: string;
        externalOrderRef: string;
      };
      expect(shopifyOrder.channel).toBe('SHOPIFY');
      expect(shopifyOrder.status).toBe('CONFIRMED');
      expect(shopifyOrder.externalOrderRef).toBe(`#E2E-${stamp}`);

      const detail = await owner.page.request.get(`/api/v1/sales-orders/${shopifyOrder.id}`);
      expect(detail.ok()).toBeTruthy();
      const shopifyDetail = (await detail.json()) as {
        lines: Array<{ sku: string; qtyOrdered: number }>;
      };
      expect(shopifyDetail.lines.some((l) => l.sku === WIDGET_S_SKU)).toBeTruthy();

      const syncLogs = await owner.page.request.get('/api/v1/integrations/sync-logs?system=SHOPIFY');
      expect(syncLogs.ok()).toBeTruthy();
      const logs = (await syncLogs.json()) as Array<{
        entityType: string;
        status: string;
        entityId: string;
      }>;
      expect(logs.some((l) => l.entityId === shopifyOrder.id && l.status === 'SUCCESS')).toBeTruthy();

      const x12 =
        'ISA*00*          *00*          *ZZ*PARTNER        *ZZ*INVSYS         *' +
        '260713*1200*U*00401*000000001*0*P*>~' +
        'ST*850*0001~' +
        `BEG*00*NE*PO-E2E-${stamp}**260713~` +
        `PO1*3*EA*10.00**VP*${WIDGET_S_SKU}~` +
        'SE*4*0001~' +
        'GE*1*0001~' +
        'IEA*1*000000001~';

      const ediRes = await owner.page.request.post('/api/v1/integrations/inbound/EDI', {
        data: x12,
        headers: {
          'Content-Type': 'text/plain',
          'X-Trading-Partner-Id': DEMO_EDI_PARTNER_ID,
        },
      });
      expect(ediRes.ok(), await ediRes.text()).toBeTruthy();
      const ediOrder = (await ediRes.json()) as {
        id: string;
        channel: string;
        status: string;
        externalOrderRef: string;
      };
      expect(ediOrder.channel).toBe('EDI');
      expect(ediOrder.status).toBe('CONFIRMED');
      expect(ediOrder.externalOrderRef).toBe(`PO-E2E-${stamp}`);

      const ediDetail = await owner.page.request.get(`/api/v1/sales-orders/${ediOrder.id}`);
      expect(ediDetail.ok()).toBeTruthy();
      const ediBody = (await ediDetail.json()) as {
        lines: Array<{ sku: string; qtyOrdered: number | string }>;
      };
      const widgetLine = ediBody.lines.find((l) => l.sku === WIDGET_S_SKU);
      expect(widgetLine).toBeTruthy();
      expect(Number(widgetLine!.qtyOrdered)).toBe(3);

      const amazon = await owner.page.request.post('/api/v1/integrations/inbound/AMAZON', {
        data: { name: '#nope', line_items: [] },
      });
      expect(amazon.status()).toBe(400);
    } finally {
      await owner.close();
    }
  });
});
