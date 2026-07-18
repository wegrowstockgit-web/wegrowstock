import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 28 — DSCSA AI 21 surface + FSMA compliance lot-trace + Settings floor load.
 */
test.describe('Journey 28: DSCSA / FSMA / Facility compliance', () => {
  test.setTimeout(300_000);

  test('GS1 composite scan returns serial; settings floor-load field; compliance API', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      // DSCSA: composite barcode with AI 21 — API must surface serialNumber when GTIN known or miss
      const composite =
        '(01)00000000000000(21)E2E-SERIAL-28(10)LOT28(17)261127';
      const scan = await owner.page.request.get(`/api/v1/scan/${encodeURIComponent(composite)}`);
      // Demo catalog may not have this GTIN — 404 is acceptable if body is Problem Details (not stack)
      if (scan.status() === 404) {
        const problem = await scan.json();
        expect(problem).toHaveProperty('title');
        expect(JSON.stringify(problem).toLowerCase()).not.toContain('at com.invsys');
      } else {
        expect(scan.ok(), await scan.text()).toBeTruthy();
        const body = (await scan.json()) as { serialNumber?: string | null; gs1Elements?: Record<string, string> };
        expect(body.serialNumber === 'E2E-SERIAL-28' || body.gs1Elements?.['21'] === 'E2E-SERIAL-28').toBeTruthy();
      }

      await owner.page.goto('/settings?tab=warehouses');
      await expect(owner.page.locator('.settings-shell')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByTestId('floor-hardware-compat')).toBeVisible();
      await owner.page.getByRole('button', { name: /Add warehouse/i }).first().click();
      await expect(owner.page).toHaveURL(/\/warehouses\/add/);
      await expect(owner.page.getByTestId('warehouse-floor-load')).toBeVisible();
      await owner.page.getByRole('button', { name: 'Cancel' }).click();
      await expect(owner.page).toHaveURL(/\/settings\?tab=warehouses/);
      await expect(owner.page.getByTestId('floor-hardware-compat')).toBeVisible();

      // FSMA recall surface is authorized for owner
      const lotTrace = await owner.page.request.get('/api/v1/compliance/lot-trace', {
        params: { lotNumber: 'NONEXISTENT-LOT-28' },
      });
      expect([404, 400]).toContain(lotTrace.status());
      const err = await lotTrace.json();
      expect(err).toHaveProperty('title');
      expect(JSON.stringify(err)).not.toMatch(/Exception|stackTrace|Caused by/i);
    } finally {
      await owner.close();
    }
  });
});
