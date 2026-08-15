import { completeScannerPin, expect, test } from '../../e2e/fixtures/roleFixture';
import { contextForRole } from '../../e2e/journeys/helpers';

async function enterUnlockPin(page: import('@playwright/test').Page, pin: string): Promise<void> {
  for (const digit of pin) {
    await page.getByTestId(`scanner-unlock-digit-${digit}`).click();
  }
}

/**
 * Mobile-Scanner — idle PIN lock wipes AES key; unlock reconstructs via PBKDF2;
 * 5 failures hard-logout.
 */
test.describe('Scanner PIN idle lock', () => {
  test.setTimeout(180_000);

  test('locks on demand, unlocks with PIN, wipes after 5 failures', async ({ browser }, testInfo) => {
    expect(testInfo.project.name).toBe('Mobile-Scanner');

    const picker = await contextForRole(browser, 'picker');
    try {
      const page = picker.page;
      await page.goto('/fulfillment');
      await completeScannerPin(page);
      await expect(page.getByTestId('scanner-lock-overlay')).toHaveCount(0);
      await expect(page.getByTestId('scanner-pin-setup-overlay')).toHaveCount(0);

      await page.evaluate(() => {
        (
          window as Window & { __INVSYS_SCANNER_LOCK__?: { lockDevice: () => void } }
        ).__INVSYS_SCANNER_LOCK__?.lockDevice();
      });

      await expect(page.getByTestId('scanner-lock-overlay')).toBeVisible({ timeout: 10_000 });
      await expect(page.getByTestId('scanner-unlock-keypad')).toBeVisible();

      // Wrong PIN — dots flash error, overlay stays; wait until input clears.
      await enterUnlockPin(page, '9999');
      await expect(page.getByTestId('scanner-lock-overlay')).toBeVisible();
      await expect(page.locator('[data-testid="scanner-unlock-dots"] [data-filled="true"]')).toHaveCount(
        0,
        { timeout: 5_000 },
      );

      // Correct shift PIN (enrolled by contextForRole as 1234).
      await enterUnlockPin(page, '1234');
      await expect(page.getByTestId('scanner-lock-overlay')).toBeHidden({ timeout: 20_000 });

      // Brute-force wipe: lock again and fail 5 times.
      await page.evaluate(() => {
        (
          window as Window & { __INVSYS_SCANNER_LOCK__?: { lockDevice: () => void } }
        ).__INVSYS_SCANNER_LOCK__?.lockDevice();
      });
      await expect(page.getByTestId('scanner-lock-overlay')).toBeVisible();

      for (let i = 0; i < 5; i += 1) {
        await enterUnlockPin(page, '0000');
        await expect(
          page.locator('[data-testid="scanner-unlock-dots"] [data-filled="true"]'),
        ).toHaveCount(0, { timeout: 5_000 });
      }

      await expect(page).toHaveURL(/\/login/, { timeout: 20_000 });
    } finally {
      await picker.close();
    }
  });
});
