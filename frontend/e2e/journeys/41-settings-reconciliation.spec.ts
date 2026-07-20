import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Settings → Reconciliation: owner loads financial truth + sync drift without 500s.
 */
test.describe('Settings reconciliation report', () => {
  test.setTimeout(120_000);

  test('owner opens reconciliation tab and sees financial truth', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const reportOk = owner.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/reports/reconciliation') &&
          res.request().method() === 'GET' &&
          res.status() === 200,
        { timeout: 30_000 },
      );

      await owner.page.goto('/settings?tab=reconciliation');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await expect(owner.page.getByRole('button', { name: 'Reconciliation' })).toBeVisible({
        timeout: 20_000,
      });
      await reportOk;

      await expect(owner.page.getByTestId('reconciliation-report')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByText('Financial truth')).toBeVisible();
      await expect(owner.page.getByTestId('reconciliation-physical-value')).toBeVisible();
      await expect(owner.page.getByText(/Unable to load reconciliation report/i)).toHaveCount(0);

      // Sync drift card: either aligned empty-state or a table of FAILED rows (null entity_id safe).
      await expect(
        owner.page
          .getByText(/No sync failures|Sync drift log|AMAZON|books are aligned/i)
          .first(),
      ).toBeVisible({ timeout: 10_000 });
    } finally {
      await owner.close();
    }
  });

  test('reconciliation API returns 200 with sync drifts for demo tenant', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const res = await owner.page.request.get('/api/v1/reports/reconciliation');
      expect(res.status()).toBe(200);
      const body = (await res.json()) as {
        physicalInventoryValue: number;
        accountingInventoryValue: number;
        driftAmount: number;
        currency: string;
        mappedAccounts: number;
        syncDrifts: { system: string; entityId: string | null; status: string }[];
      };
      expect(body.currency).toBeTruthy();
      expect(typeof body.physicalInventoryValue).toBe('number');
      expect(Array.isArray(body.syncDrifts)).toBe(true);
      for (const drift of body.syncDrifts) {
        expect(drift.entityId == null || typeof drift.entityId === 'string').toBe(true);
        expect(drift.status).toMatch(/FAILED/i);
      }
    } finally {
      await owner.close();
    }
  });
});
