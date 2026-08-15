import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * Shared helpers for the 6-step receiving → allocation driver.js tour.
 * Used by support-multipage-tour, admin, and picker persona suites.
 */

export async function clickTourNext(page: Page): Promise<void> {
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
export async function unlockFloorPreservingTour(page: Page, pin = '1234'): Promise<void> {
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

export async function startReceivingToAllocationTour(page: Page): Promise<void> {
  await page.evaluate(() => {
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
}

export async function clearActiveTour(page: Page): Promise<void> {
  await page.evaluate(() => {
    const hook = (
      window as Window & { __INVSYS_PREFERENCES__?: { clearTour: () => void } }
    ).__INVSYS_PREFERENCES__;
    hook?.clearTour?.();
  });
  await page.keyboard.press('Escape').catch(() => undefined);
}

/**
 * Drive the full 6-step journey and assert progress counters never reset across hops.
 * Callers must already be authenticated on `/purchase-orders` with tour anchors mounted.
 */
export async function assertReceivingToAllocationTour(page: Page): Promise<void> {
  await startReceivingToAllocationTour(page);

  const popover = page.locator('.driver-popover');
  await expect(popover).toBeVisible({ timeout: 20_000 });
  await expect(popover).toContainText(/Step 1 of 6/i);

  await clickTourNext(page);
  await expect(popover).toBeVisible({ timeout: 20_000 });
  await expect(popover).toContainText(/Step 2 of 6/i);

  await clickTourNext(page);
  await page.waitForURL(/\/inbound\/receive/, { timeout: 20_000 });
  await unlockFloorPreservingTour(page);
  await expect(popover).toBeVisible({ timeout: 25_000 });
  await expect(popover).toContainText(/Step 3 of 6/i);

  await clickTourNext(page);
  await expect(popover).toBeVisible({ timeout: 20_000 });
  await expect(popover).toContainText(/Step 4 of 6/i);

  await clickTourNext(page);
  await page.waitForURL(/\/sales-orders/, { timeout: 20_000 });
  await expect(popover).toBeVisible({ timeout: 25_000 });
  await expect(popover).toContainText(/Step 5 of 6/i);

  await clickTourNext(page);
  await expect(popover).toBeVisible({ timeout: 20_000 });
  await expect(popover).toContainText(/Step 6 of 6/i);

  await clickTourNext(page);
  await expect(popover).toHaveCount(0, { timeout: 15_000 });
}
