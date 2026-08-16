import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from './fixtures/roleFixture';
import { contextForRole } from './journeys/helpers';

/**
 * Page Info overlay — portaled full-viewport panel (header backdrop-blur must not clip it).
 */
test.describe('Page help overlay', () => {
  test.setTimeout(120_000);

  test('desktop: full-viewport right panel on purchase orders', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await owner.page.goto('/purchase-orders');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await expect(owner.page.getByTestId('page-help-trigger')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('page-help-trigger').click();

      const panel = owner.page.getByTestId('page-help-panel');
      await expect(panel).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('page-help-body')).toContainText(/purchase|supplier|Purpose/i);

      const inBody = await panel.evaluate((el) => el.parentElement?.parentElement === document.body);
      expect(inBody).toBe(true);

      const box = await panel.boundingBox();
      expect(box).toBeTruthy();
      expect(box!.y).toBeLessThanOrEqual(4);
      expect(box!.height).toBeGreaterThan(700);
      expect(box!.width).toBeGreaterThan(320);

      await expect(owner.page.getByTestId('app-shell').getByRole('button', { name: 'New PO', exact: true })).toBeVisible();

      await owner.page.getByTestId('page-help-panel').getByRole('button', { name: 'Close', exact: true }).click();
      await expect(owner.page.getByTestId('page-help-body')).toHaveCount(0);
    } finally {
      await owner.close();
    }
  });

  test('desktop: dashboard panel clears page CTAs', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1440, height: 900 });
      await owner.page.goto('/dashboard');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await owner.page.getByTestId('page-help-trigger').click();
      const panel = owner.page.getByTestId('page-help-panel');
      await expect(panel).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('page-help-body')).toContainText(/Command center|KPI/i);

      const box = await panel.boundingBox();
      expect(box!.height).toBeGreaterThan(800);
      expect(box!.x + box!.width).toBeGreaterThan(1400);

      await owner.page.keyboard.press('Escape');
      await expect(owner.page.getByTestId('page-help-body')).toHaveCount(0);
    } finally {
      await owner.close();
    }
  });

  test('mobile: bottom sheet on sales orders', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.setViewportSize({ width: 390, height: 844 });
      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('page-help-trigger').click();

      const panel = manager.page.getByTestId('page-help-panel');
      await expect(panel).toBeVisible({ timeout: 15_000 });
      await expect(manager.page.getByTestId('page-help-body')).toContainText(/Un-allocate|Cancel/i);

      const inBody = await panel.evaluate((el) => el.parentElement?.parentElement === document.body);
      expect(inBody).toBe(true);

      const box = await panel.boundingBox();
      expect(box).toBeTruthy();
      // Bottom sheet: starts below the top of the viewport and spans nearly full width.
      expect(box!.y).toBeGreaterThan(80);
      expect(box!.width).toBeGreaterThan(300);
      expect(box!.height).toBeGreaterThan(200);

      await manager.page
        .getByTestId('page-help-panel')
        .getByRole('button', { name: 'Close', exact: true })
        .click();
      await expect(manager.page.getByTestId('page-help-body')).toHaveCount(0);
    } finally {
      await manager.close();
    }
  });

  test('floor shell + inbound receive still open playbooks', async ({ browser }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 1280, height: 800 });
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await dismissOnboardingTourIfPresent(picker.page);

      await picker.page.getByTestId('page-help-trigger').click();
      await expect(picker.page.getByTestId('page-help-body')).toBeVisible({ timeout: 15_000 });
      const floorBox = await picker.page.getByTestId('page-help-panel').boundingBox();
      expect(floorBox!.height).toBeGreaterThan(700);
      await picker.page.keyboard.press('Escape');

      await picker.page.goto('/inbound/receive');
      await completeScannerPin(picker.page);
      await dismissOnboardingTourIfPresent(picker.page);
      await picker.page.getByTestId('page-help-trigger').click();
      await expect(picker.page.getByTestId('page-help-body')).toBeVisible({ timeout: 15_000 });
      await expect(picker.page.getByText(/Skip & Flag|stock correction|undo|mistake/i).first()).toBeVisible();
    } finally {
      await picker.close();
    }
  });

  test('quick action navigates from sales orders into fulfillment', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.setViewportSize({ width: 1280, height: 800 });
      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('page-help-trigger').click();
      await expect(manager.page.getByTestId('page-help-quick-actions')).toBeVisible({ timeout: 15_000 });
      await manager.page.getByTestId('page-help-quick-action').filter({ hasText: /Go to Fulfillment/i }).click();
      await expect(manager.page).toHaveURL(/\/fulfillment/, { timeout: 15_000 });
      await expect(manager.page.getByTestId('page-help-body')).toHaveCount(0);
    } finally {
      await manager.close();
    }
  });
});
