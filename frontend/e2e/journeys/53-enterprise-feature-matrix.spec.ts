import { expect, test } from '@playwright/test';
import { contextForRole, DEMO_PASSWORD } from './helpers';

/**
 * Real functional coverage for the 8 enterprise market-gap APIs + UI shells.
 * Uses HttpOnly cookie session (same as production) via contextForRole.
 * Skips cleanly when V097 enterprise APIs are not deployed yet.
 */
test.describe('Enterprise feature matrix', () => {
  test('API + UI smoke for thermal, MRP, pallet, permissions, cluster pick', async ({
    browser,
  }) => {
    test.setTimeout(120_000);

    const { page, close } = await contextForRole(browser, 'owner');
    try {
      const printers = await page.request.get('/api/v1/thermal-printers');
      test.skip(
        printers.status() === 404,
        'V097 enterprise APIs not deployed — rebuild backend (deploy.bat)',
      );
      expect(printers.ok(), await printers.text()).toBeTruthy();

      const createPrinter = await page.request.post('/api/v1/thermal-printers', {
        data: {
          name: `E2E-Printer-${Date.now()}`,
          printerType: 'DIRECT_SOCKET',
          ipAddress: '127.0.0.1',
          port: 9100,
          isDefault: true,
        },
      });
      expect(createPrinter.ok(), await createPrinter.text()).toBeTruthy();
      const printer = (await createPrinter.json()) as { id: string };

      const printRes = await page.request.post(`/api/v1/thermal-printers/${printer.id}/print`, {
        data: { zpl: '^XA^FO50,50^FDINVSYS E2E^FS^XZ' },
      });
      // Socket may refuse on CI — accept success or printer-unreachable server errors
      expect([200, 409, 422, 500, 502, 503]).toContain(printRes.status());

      const mrpSuggestions = await page.request.get('/api/v1/purchasing/mrp/suggestions');
      expect(mrpSuggestions.ok(), await mrpSuggestions.text()).toBeTruthy();
      expect(Array.isArray(await mrpSuggestions.json())).toBeTruthy();

      const mrpCalc = await page.request.post('/api/v1/purchasing/mrp/calculate', { data: {} });
      expect(mrpCalc.ok(), await mrpCalc.text()).toBeTruthy();
      const mrpBody = (await mrpCalc.json()) as {
        createdPurchaseOrders: unknown[];
        suggestions: unknown[];
      };
      expect(mrpBody).toHaveProperty('createdPurchaseOrders');
      expect(mrpBody).toHaveProperty('suggestions');

      const pallet = await page.request.post('/api/v1/pallet-manifests', {
        data: { carrierName: 'E2E Carrier' },
      });
      expect(pallet.ok(), await pallet.text()).toBeTruthy();
      const palletBody = (await pallet.json()) as {
        id: string;
        sscc18: string;
        status: string;
      };
      expect(palletBody.sscc18).toBeTruthy();
      expect(palletBody.status).toBe('BUILDING');

      const seal = await page.request.post(`/api/v1/pallet-manifests/${palletBody.id}/seal`, {
        data: {},
      });
      expect(seal.ok(), await seal.text()).toBeTruthy();
      const sealed = (await seal.json()) as { status: string; bolNumber: string };
      expect(sealed.status).toBe('SEALED');
      expect(sealed.bolNumber).toBeTruthy();

      const perms = await page.request.get('/api/v1/settings/role-permissions');
      expect(perms.ok(), await perms.text()).toBeTruthy();
      const matrix = (await perms.json()) as {
        roles: unknown[];
        permissionKeys: string[];
      };
      expect(matrix.roles?.length).toBeGreaterThan(0);
      expect(matrix.permissionKeys).toContain('inventory:cost:view');

      const catalog = await page.request.get('/api/v1/settings/role-permissions/catalog');
      expect(catalog.ok()).toBeTruthy();

      await page.goto('/mrp');
      await expect(page.getByTestId('mrp-reorder-workspace')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('mrp-consolidate-button')).toBeVisible();

      await page.goto('/pallet-manifests');
      await expect(page.getByTestId('pallet-manifest-workspace')).toBeVisible({ timeout: 15_000 });

      await page.goto('/cluster-pick');
      await expect(page.getByTestId('cluster-picker-view')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('cluster-slot-grid')).toBeVisible();

      await page.goto('/settings?tab=users');
      await expect(page.getByTestId('role-permissions-matrix')).toBeVisible({ timeout: 15_000 });
    } finally {
      await close();
    }
  });

  test('UI login reaches MRP workspace when APIs are live', async ({ page }) => {
    test.setTimeout(60_000);
    await page.goto('/login');
    await page.getByLabel('Email').fill('owner@demo.test');
    await page.getByLabel('Password').fill(DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 20_000 });
    await page.goto('/mrp');
    // Page shell must render even if suggestions API 404s on undeployed stacks
    await expect(page.getByRole('heading', { name: /MRP reorder/i })).toBeVisible({
      timeout: 15_000,
    });
  });
});
