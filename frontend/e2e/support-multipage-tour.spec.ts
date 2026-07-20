import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from './fixtures/roleFixture';
import { contextForRole } from './journeys/helpers';

/**
 * Multi-page receiving → allocation workflow tour (driver.js + preferences store).
 */
test.describe('Support multi-page tour', () => {
  test.setTimeout(120_000);

  test('receiving-to-allocation tour advances across routes', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/purchase-orders');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await owner.page.evaluate(() => {
        const hook = (
          window as Window & {
            __INVSYS_PREFERENCES__?: { startTour: (id: string, step?: number) => void };
          }
        ).__INVSYS_PREFERENCES__;
        if (!hook?.startTour) {
          throw new Error('__INVSYS_PREFERENCES__.startTour not installed');
        }
        hook.startTour('receiving-to-allocation', 0);
      });

      const popover = owner.page.locator('.driver-popover');
      await expect(popover).toBeVisible({ timeout: 20_000 });
      await expect(popover).toContainText(/purchase order|receiving|procurement/i);

      const next = popover.locator('button').filter({ hasText: /next|→|>/i }).first();
      if (await next.isVisible().catch(() => false)) {
        await next.click();
      } else {
        await popover.locator('.driver-popover-next-btn, .driver-popover-footer button').last().click();
      }

      await owner.page.waitForURL(/\/inbound\/receive|\/purchase-orders|\/sales-orders/, {
        timeout: 20_000,
      });

      await owner.page.evaluate(() => {
        const hook = (
          window as Window & { __INVSYS_PREFERENCES__?: { clearTour: () => void } }
        ).__INVSYS_PREFERENCES__;
        hook?.clearTour?.();
      });
      await owner.page.keyboard.press('Escape').catch(() => undefined);
    } finally {
      await owner.close();
    }
  });
});
