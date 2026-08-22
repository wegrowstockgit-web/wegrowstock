import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

test.describe('Dynamic page knowledge overlay', () => {
  test.setTimeout(120_000);

  test('preloaded help shows category, mistakes, and ledger recovery', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await owner.page.goto('/purchase-orders');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await expect(owner.page.getByTestId('page-help-trigger')).toBeVisible({ timeout: 20_000 });
      await owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/page-knowledge/all') && res.ok(),
        { timeout: 30_000 },
      ).catch(() => undefined);

      await owner.page.getByTestId('page-help-trigger').click();
      const panel = owner.page.getByTestId('page-help-panel');
      await expect(panel).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('page-help-dynamic')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('page-help-dynamic')).toContainText(/Inbound/i);
      await expect(owner.page.getByTestId('page-help-summary')).toContainText(/purchase|supplier|Purpose/i);
      await expect(owner.page.getByTestId('page-help-privileges')).toContainText(/Warehouse Managers/i);
      await expect(owner.page.getByTestId('page-help-key-actions')).toBeVisible();
      await expect(owner.page.getByTestId('page-help-mistakes')).toContainText(/wrong supplier price|duplicate PO|Reverse/i);
      await expect(owner.page.getByTestId('page-help-pro-tip')).toBeVisible();
      await expect(panel).not.toContainText(/VetAztek|VetNexus/i);
    } finally {
      await owner.close();
    }
  });
});
