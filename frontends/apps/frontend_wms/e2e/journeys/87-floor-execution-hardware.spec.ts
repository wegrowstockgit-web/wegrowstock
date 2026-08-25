import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole, expectFulfillmentSurface } from './helpers';

/**
 * Journey 87 — Floor shell on scanner widths, auto labor from LPN scans, pack weight override.
 */
test.describe('Journey 87: Floor execution UX and hardware automation', () => {
  test.setTimeout(180_000);

  test('scanner header stays compact; LPN scan clocks picking; pack weight can override', async ({
    browser,
  }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 360, height: 640 });
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({ timeout: 30_000 });
      await dismissOnboardingTourIfPresent(picker.page);
      await expectFulfillmentSurface(picker.page);

      const header = picker.page.locator('header').first();
      const headerBox = await header.boundingBox();
      expect(headerBox).toBeTruthy();
      expect(headerBox!.width).toBeLessThanOrEqual(360);
      expect(headerBox!.x + headerBox!.width).toBeLessThanOrEqual(361);

      const badge = picker.page.getByTestId('network-status-badge');
      await expect(badge).toBeVisible();
      await expect(badge.getByText('Connected')).not.toBeVisible();

      await expect(picker.page.getByTestId('labor-clock-menu-toggle')).toBeVisible();
      await expect(picker.page.getByTestId('labor-clock-in')).toHaveCount(0);

      await picker.page.getByTestId('labor-clock-menu-toggle').click();
      await expect(picker.page.getByTestId('labor-clock-sheet')).toBeVisible();
      await picker.page.getByTestId('labor-clock-sheet-backdrop').click({ position: { x: 8, y: 8 } });
      await expect(picker.page.getByTestId('labor-clock-sheet')).toHaveCount(0);

      await picker.page.evaluate(() => {
        window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: 'LPN-FLOOR-87' } }));
      });
      await expect
        .poll(
          async () => {
            const me = await picker.page.request.get('/api/v1/labor/me');
            if (!me.ok()) return '';
            const body = (await me.json()) as { active?: boolean; currentActivity?: string };
            return body.active ? (body.currentActivity ?? '') : '';
          },
          { timeout: 15_000 },
        )
        .toBe('PICKING');

      await picker.page.getByRole('button', { name: 'Pack', exact: true }).click();
      await expect(picker.page.getByTestId('pack-weight-input')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('pack-weight-manual-override')).toBeVisible();
      await picker.page.getByTestId('pack-weight-manual-override').click();
      await expect(picker.page.getByTestId('pack-weight-input')).toBeEditable();
    } finally {
      await picker.close();
    }
  });
});
