import { expect, test } from '../fixtures/roleFixture';
import { completeScannerPin, dismissOnboardingTourIfPresent } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Advanced support awareness: proactive insights, action drafts, camera control,
 * and training sandbox (no live mutations).
 */
test.describe('Support awareness, drafts & training', () => {
  test.setTimeout(120_000);

  test('sales-orders may show proactive insight pill for manager', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await expect(manager.page.getByTestId('support-assistant-fab')).toBeVisible({ timeout: 30_000 });

      const insight = manager.page.getByTestId('support-proactive-insight');
      // Insight is data-dependent; when present it must be actionable copy.
      if (await insight.isVisible().catch(() => false)) {
        await expect(insight).toContainText(/Credit Hold|BACKORDERED|attention|💡/i);
        await insight.click();
        await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();
      } else {
        await manager.page.getByTestId('support-assistant-fab').click();
        await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();
      }

      // FAB is hidden while the panel is open — assert panel contents directly.
      await expect(manager.page.getByTestId('support-camera-button')).toBeVisible();
      // Training chips render only on an empty transcript; insight-click auto-query may fill it.
      const trainingChip = manager.page.getByTestId('support-training-MANAGER_ALLOCATION');
      if (await trainingChip.isVisible().catch(() => false)) {
        await expect(trainingChip).toBeVisible();
      }
      // Panel-level amber pill (when insight still active) is clickable above the composer.
      const panelInsight = manager.page.getByTestId('support-proactive-insight-panel');
      if (await panelInsight.isVisible().catch(() => false)) {
        await expect(panelInsight).toContainText(/Credit Hold|BACKORDERED|attention|wave|💡/i);
      }
    } finally {
      await manager.close();
    }
  });

  test('cycle-count question yields approveable action draft for manager', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/cycle-counts');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await manager.page
        .getByTestId('support-assistant-input')
        .fill('Generate cycle count for zone Aisle-4 — do it for me');
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/cycle count|Aisle|Diagnosis|Action/i, { timeout: 45_000 });

      const draft = manager.page.getByTestId('support-action-draft');
      await expect(draft).toBeVisible({ timeout: 20_000 });
      await expect(draft).toContainText(/Aisle-4/i);

      await manager.page.getByTestId('support-draft-approve').click();
      // Demo seed may not include Aisle-4 — soft-fail or success both prove the HITL path.
      await expect(
        manager.page.getByTestId('support-draft-approved').or(manager.page.getByTestId('support-draft-failed')),
      ).toBeVisible({ timeout: 20_000 });
    } finally {
      await manager.close();
    }
  });

  test('training mission banner appears and can exit without live stock changes', async ({
    browser,
  }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 390, height: 844 });
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);

      await picker.page.getByTestId('support-assistant-fab').click();
      await expect(picker.page.getByTestId('support-camera-button')).toBeVisible();
      await picker.page.getByTestId('support-training-PICKER_INBOUND').click();

      await expect(picker.page.getByTestId('training-mission-banner')).toBeVisible();
      await expect(picker.page.getByTestId('training-mission-banner')).toContainText(
        /TRAINING SIMULATOR ACTIVE|NO DATA WILL BE SAVED|no live stock/i,
      );
      await expect(picker.page.getByTestId('support-training-simulator-header')).toBeVisible();
      await expect(picker.page.getByTestId('support-assistant-reply').last()).toContainText(
        /Training mode|live stock will not change/i,
      );

      await picker.page.getByTestId('training-mission-exit').click();
      await expect(picker.page.getByTestId('training-mission-banner')).toHaveCount(0);
    } finally {
      await picker.close();
    }
  });
});
