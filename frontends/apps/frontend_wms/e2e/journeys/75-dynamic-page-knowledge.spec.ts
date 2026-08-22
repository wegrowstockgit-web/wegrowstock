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
      await expect(owner.page.getByTestId('page-help-summary')).toContainText(
        /Warehouse Managers|Inbound Receive|submit POs/i,
      );
      await expect(owner.page.getByTestId('page-help-key-actions')).toContainText(/data grid|Receiving Progress/i);
      await expect(owner.page.getByTestId('page-help-privileges')).toContainText(/Warehouse Managers/i);
      await expect(owner.page.getByTestId('page-help-key-actions')).toBeVisible();
      await expect(owner.page.getByTestId('page-help-mistakes')).toContainText(/wrong supplier price|duplicate PO|Reverse/i);
      await expect(owner.page.getByTestId('page-help-pro-tip')).toBeVisible();
      await expect(panel).not.toContainText(/VetAztek|VetNexus/i);
      await expect(owner.page.getByTestId('page-help-mistakes')).toContainText(
        /customs bill|Landed Cost Allocation/i,
      );
    } finally {
      await owner.close();
    }
  });

  test('receive and landed-cost playbooks cover UoM, cross-dock, lots, and freight', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await owner.page.goto('/inbound/receive');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await owner.page.waitForResponse(
        (res) => res.url().includes('/api/v1/page-knowledge/all') && res.ok(),
        { timeout: 30_000 },
      ).catch(() => undefined);

      await owner.page.getByTestId('page-help-trigger').click();
      await expect(owner.page.getByTestId('page-help-dynamic')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('page-help-mistakes')).toContainText(/Pallets|Cases|UoM|Cross-Dock/i);
      await expect(owner.page.getByTestId('page-help-pro-tip')).toContainText(/FSMA|DSCSA|Lot Number/i);
      await owner.page.getByTestId('page-help-panel').getByRole('button', { name: 'Close', exact: true }).click();

      const knowledge = await owner.page.request.get('/api/v1/page-knowledge?route=/inventory/landed-costs');
      expect(knowledge.ok()).toBeTruthy();
      const body = await knowledge.json();
      expect(JSON.stringify(body)).toMatch(/Landed Cost Allocation|Do NOT edit the PO/i);
    } finally {
      await owner.close();
    }
  });
});
