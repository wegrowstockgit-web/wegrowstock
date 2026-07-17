import { test } from '@playwright/test';
import {
  CLIENT_A_METRO_ID,
  CLIENT_B_RETAIL_ID,
  apiJson,
  contextForRole,
  expect,
  inviteAndAcceptB2b,
} from './helpers';

/**
 * Track 15 — 3PL multi-owner SLAs → StorageAccrualWorker → isolated Showroom billing dashboards.
 */
test.describe.serial('Journey 15: 3PL Multi-Owner Billing Accruals', () => {
  test('pallet vs cubic accruals surface on Client A / Client B portals', async ({ browser }) => {
    const admin = await contextForRole(browser, 'admin');
    let clientB: { close: () => Promise<void>; page: import('@playwright/test').Page } | null =
      null;
    const b2b = await contextForRole(browser, 'b2b');

    try {
      // Client A = Metro = PALLET_POSITION; Client B = Retail = CUBIC_VOLUME
      await apiJson(admin.page, `/api/v1/customers/${CLIENT_A_METRO_ID}/billing/sla`, {
        method: 'PUT',
        body: JSON.stringify({
          storageMode: 'PALLET_POSITION',
          ratePerUnit: 1.25,
          pickFeePerItem: 0.35,
        }),
      });
      await apiJson(admin.page, `/api/v1/customers/${CLIENT_B_RETAIL_ID}/billing/sla`, {
        method: 'PUT',
        body: JSON.stringify({
          storageMode: 'CUBIC_VOLUME',
          ratePerUnit: 0.05,
          pickFeePerItem: 0.25,
        }),
      });

      const today = new Date().toISOString().slice(0, 10);
      const accrual = await apiJson<{ created: number; accrualDate: string }>(
        admin.page,
        `/api/v1/admin/test/accruals/run?date=${today}`,
        { method: 'POST', body: '{}' },
      );
      expect(accrual.accrualDate).toBe(today);
      // created may be 0 if already accrued today — still assert dashboards show mode + rows

      // Client A API + UI (seed b2b@demo.test → Metro)
      await expect
        .poll(async () => {
          const res = await b2b.page.request.get('/api/v1/showroom/billing/accruals');
          if (!res.ok()) return 0;
          const body = (await res.json()) as {
            sla?: { storageMode?: string };
            monthToDateTotal?: number;
          };
          expect(body.sla?.storageMode).toBe('PALLET_POSITION');
          return Number(body.monthToDateTotal ?? 0);
        }, { timeout: 20_000 })
        .toBeGreaterThan(0);
      // Client B — invite Retail Partners portal user (isolated showroom session)
      const retailEmail = `client.b.${Date.now()}@demo.test`;
      clientB = await inviteAndAcceptB2b(browser, admin.page, {
        email: retailEmail,
        customerId: CLIENT_B_RETAIL_ID,
        displayName: 'Retail',
      });

      await expect
        .poll(async () => {
          const res = await clientB!.page.request.get('/api/v1/showroom/billing/accruals');
          if (!res.ok()) return 0;
          const body = (await res.json()) as {
            sla?: { storageMode?: string };
            monthToDateTotal?: number;
          };
          expect(body.sla?.storageMode).toBe('CUBIC_VOLUME');
          return Number(body.monthToDateTotal ?? 0);
        }, { timeout: 20_000 })
        .toBeGreaterThan(0);

      // Surface A/B UI smoke (API already asserted mode isolation)
      await b2b.page.goto('/showroom/billing');
      await clientB.page.goto('/showroom/billing');
      await expect(b2b.page).toHaveURL(/showroom\/billing/);
      await expect(clientB.page).toHaveURL(/showroom\/billing/);

      // Office cross-check: modes differ and amounts reflect distinct engines
      const officeA = await apiJson<{
        sla: { storageMode: string };
        unbilledTotal: number;
      }>(admin.page, `/api/v1/customers/${CLIENT_A_METRO_ID}/billing`);
      const officeB = await apiJson<{
        sla: { storageMode: string };
        unbilledTotal: number;
      }>(admin.page, `/api/v1/customers/${CLIENT_B_RETAIL_ID}/billing`);
      expect(officeA.sla.storageMode).toBe('PALLET_POSITION');
      expect(officeB.sla.storageMode).toBe('CUBIC_VOLUME');
      expect(Number(officeA.unbilledTotal)).toBeGreaterThan(0);
      expect(Number(officeB.unbilledTotal)).toBeGreaterThan(0);
    } finally {
      await clientB?.close();
      await b2b.close();
      await admin.close();
    }
  });
});
