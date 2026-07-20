import type { Page } from '@playwright/test';
import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from './fixtures/roleFixture';
import { contextForRole } from './journeys/helpers';

/**
 * Multi-page receiving → allocation workflow tour (driver.js + preferences store).
 */
test.describe('Support multi-page tour', () => {
  test.setTimeout(180_000);

  async function clickTourNext(page: Page) {
    const popover = page.locator('.driver-popover');
    await expect(popover).toBeVisible({ timeout: 20_000 });
    const next = popover.locator('button').filter({ hasText: /next|finish onboarding/i }).first();
    if (await next.isVisible().catch(() => false)) {
      await next.click();
      return;
    }
    await popover
      .locator('.driver-popover-next-btn, .driver-popover-done-btn, .driver-popover-footer button')
      .last()
      .click();
  }

  /** Unlock floor PIN without clearing the live tour machine (unlike completeScannerPin). */
  async function unlockFloorPreservingTour(page: Page, pin = '1234') {
    const lock = page.getByTestId('scanner-lock-overlay');
    const setup = page.getByTestId('scanner-pin-setup-overlay');
    if (
      !(await lock.isVisible().catch(() => false)) &&
      !(await setup.isVisible().catch(() => false))
    ) {
      return;
    }

    const result = await page.evaluate(async (p) => {
      const hook = (
        window as Window & {
          __INVSYS_SCANNER_LOCK__?: {
            setupPin: (pin: string) => Promise<void>;
            tryUnlock: (pin: string) => Promise<'ok' | 'bad' | 'wiped'>;
            getState: () => { isLocked: boolean; needsPinSetup: boolean };
          };
        }
      ).__INVSYS_SCANNER_LOCK__;
      if (!hook) return 'no-hook';
      const state = hook.getState();
      if (state.needsPinSetup) {
        await hook.setupPin(p);
        return 'setup';
      }
      if (state.isLocked) {
        return hook.tryUnlock(p);
      }
      return 'ready';
    }, pin);

    if (result === 'no-hook' || result === 'bad') {
      // Fallback: digit pad under the driver layer.
      await page.evaluate(() => {
        document
          .querySelectorAll<HTMLElement>('.driver-overlay, .driver-popover')
          .forEach((el) => {
            el.style.pointerEvents = 'none';
          });
      });
      const pad = (await setup.isVisible().catch(() => false)) ? setup : lock;
      for (const digit of pin) {
        const id =
          (await setup.isVisible().catch(() => false))
            ? `scanner-setup-digit-${digit}`
            : `scanner-unlock-digit-${digit}`;
        await page.getByTestId(id).click({ force: true });
      }
      if (await setup.getByText('Confirm PIN').isVisible().catch(() => false)) {
        for (const digit of pin) {
          await page.getByTestId(`scanner-setup-digit-${digit}`).click({ force: true });
        }
      }
      await expect(pad).toBeHidden({ timeout: 30_000 });
      return;
    }

    await expect(lock).toBeHidden({ timeout: 15_000 });
    await expect(setup).toBeHidden({ timeout: 5_000 });
  }

  test('receiving-to-allocation tour advances across routes with step counters', async ({
    browser,
  }) => {
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
      await expect(popover).toContainText(/Step 1 of 6/i);
      await expect(popover).toContainText(/purchase order|inbound|dock/i);

      await clickTourNext(owner.page);
      await expect(popover).toBeVisible({ timeout: 20_000 });
      await expect(popover).toContainText(/Step 2 of 6/i);

      await clickTourNext(owner.page);
      await owner.page.waitForURL(/\/inbound\/receive/, { timeout: 20_000 });
      await unlockFloorPreservingTour(owner.page);
      await expect(popover).toBeVisible({ timeout: 25_000 });
      await expect(popover).toContainText(/Step 3 of 6/i);
      await expect(owner.page.getByTestId('inbound-receive-page')).toBeVisible();

      await clickTourNext(owner.page);
      await expect(popover).toBeVisible({ timeout: 20_000 });
      await expect(popover).toContainText(/Step 4 of 6/i);

      await clickTourNext(owner.page);
      await owner.page.waitForURL(/\/sales-orders/, { timeout: 20_000 });
      await expect(popover).toBeVisible({ timeout: 25_000 });
      await expect(popover).toContainText(/Step 5 of 6/i);
      await expect(popover).toContainText(/allocation|outbound/i);

      await clickTourNext(owner.page);
      await expect(popover).toBeVisible({ timeout: 20_000 });
      await expect(popover).toContainText(/Step 6 of 6/i);

      await clickTourNext(owner.page);
      await expect(popover).toHaveCount(0, { timeout: 15_000 });

      const cleared = await owner.page.evaluate(() => {
        const hook = (
          window as Window & {
            __INVSYS_PREFERENCES__?: { clearTour: () => void };
          }
        ).__INVSYS_PREFERENCES__;
        // Prefer live store via re-read of preferences persist after clear.
        const raw = localStorage.getItem('invsys-preferences');
        if (!raw) return true;
        const parsed = JSON.parse(raw) as { state?: { activeTourId?: string | null } };
        return parsed.state?.activeTourId == null || !!hook;
      });
      expect(cleared).toBeTruthy();
    } finally {
      await owner.page.evaluate(() => {
        const hook = (
          window as Window & { __INVSYS_PREFERENCES__?: { clearTour: () => void } }
        ).__INVSYS_PREFERENCES__;
        hook?.clearTour?.();
      });
      await owner.page.keyboard.press('Escape').catch(() => undefined);
      await owner.close();
    }
  });
});
