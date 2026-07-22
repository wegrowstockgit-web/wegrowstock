import { expect, test } from '../fixtures/roleFixture';
import { completeScannerPin, dismissOnboardingTourIfPresent } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * End-to-end Operations Instructor: page-state payload → structured reply →
 * NAVIGATE / SPOTLIGHT chips + follow-up quick replies.
 */
test.describe('Support copilot actionable RAG', () => {
  test.setTimeout(120_000);

  test('manager gets instructor reply with chips and can navigate', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/dashboard');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();

      await manager.page
        .getByTestId('support-assistant-input')
        .fill('Why is a sales order BACKORDERED and how do I Un-allocate?');
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/Diagnosis|Action [Pp]lan|Reversal|Ledger Safety/i, {
        timeout: 45_000,
      });

      const chip = manager.page.getByTestId('support-action-chip').first();
      await expect(chip).toBeVisible({ timeout: 15_000 });
      await expect(chip).toHaveAttribute('data-action', /NAVIGATE|SPOTLIGHT/);

      await expect(manager.page.getByTestId('support-follow-ups')).toBeVisible({ timeout: 15_000 });

      const navigateChip = manager.page
        .locator('[data-testid="support-action-chip"][data-action="NAVIGATE"]')
        .first();
      if (await navigateChip.count()) {
        await navigateChip.click();
        await expect(manager.page).toHaveURL(/sales-orders|purchase-orders|exceptions|cycle-counts/, {
          timeout: 15_000,
        });
      }
    } finally {
      await manager.close();
    }
  });

  test('manager can start receiving-to-allocation walkthrough from chip', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/dashboard');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await manager.page
        .getByTestId('support-assistant-input')
        .fill('Train me end-to-end — start the receiving to allocation walkthrough');
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/Diagnosis|Action plan|Ledger Safety|Reversal/i, {
        timeout: 45_000,
      });

      const tourChip = manager.page.locator(
        '[data-testid="support-action-chip"][data-action="START_TOUR"]',
      );
      await expect(tourChip.first()).toBeVisible({ timeout: 15_000 });
      await expect(tourChip.first()).toHaveAttribute('data-target', 'receiving-to-allocation');
      await tourChip.first().click();
      await expect(manager.page.getByTestId('support-assistant-reply').last()).toContainText(
        /walkthrough|receiving-to-allocation/i,
        { timeout: 10_000 },
      );
    } finally {
      await manager.close();
    }
  });

  test('picker inbound guidance stays handheld-safe with fulfillment chip', async ({ browser }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 360, height: 640 });
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({ timeout: 30_000 });

      await picker.page.getByTestId('support-assistant-fab').click();
      await picker.page
        .getByTestId('support-assistant-input')
        .fill('How do I process an inbound shipment?');
      await picker.page.getByTestId('support-assistant-send').click();

      const reply = picker.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/scan/i, { timeout: 30_000 });
      await expect(reply).not.toContainText(/create purchase order on the desktop/i);

      const chips = picker.page.getByTestId('support-action-chip');
      await expect(chips.first()).toBeVisible({ timeout: 15_000 });
      const targets = await chips.evaluateAll((els) =>
        els.map((el) => el.getAttribute('data-target') || ''),
      );
      expect(targets.some((t) => t.includes('fulfillment') || t.includes('data-tour'))).toBeTruthy();
    } finally {
      await picker.close();
    }
  });
});
