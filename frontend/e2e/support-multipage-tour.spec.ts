import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from './fixtures/roleFixture';
import {
  assertReceivingToAllocationTour,
  clearActiveTour,
} from './fixtures/tourContinuity';
import { contextForRole } from './journeys/helpers';

/**
 * Multi-page receiving → allocation workflow tour (driver.js + preferences store).
 */
test.describe('Support multi-page tour', () => {
  test.setTimeout(180_000);

  test('receiving-to-allocation tour advances across routes with step counters', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/purchase-orders');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await assertReceivingToAllocationTour(owner.page);

      const cleared = await owner.page.evaluate(() => {
        const raw = localStorage.getItem('invsys-preferences');
        if (!raw) return true;
        const parsed = JSON.parse(raw) as { state?: { activeTourId?: string | null } };
        return parsed.state?.activeTourId == null;
      });
      expect(cleared).toBeTruthy();
    } finally {
      await clearActiveTour(owner.page);
      await owner.close();
    }
  });
});
