import { expect, test } from '../fixtures/roleFixture';

/**
 * Journey 73 — Tenant-configurable office idle soft-lock (NIST 800-63B).
 * Skips until V128 is on the live API image.
 */
test.describe('Journey 73: Desktop idle timeout', () => {
  test.setTimeout(180_000);

  test('owner can save timeout and unlock the overlay with a password', async ({ ownerPage }) => {
    const settings = await ownerPage.request.get('/api/v1/settings');
    test.skip(!settings.ok(), 'Tenant settings API is not reachable');
    const body = (await settings.json()) as { desktop_idle_timeout_minutes?: number };
    test.skip(
      body.desktop_idle_timeout_minutes == null,
      'desktop_idle_timeout_minutes not deployed (needs V128)',
    );

    const options = await ownerPage.request.get('/api/v1/auth/desktop-unlock/options');
    test.skip(!options.ok(), 'Desktop unlock API is not deployed');

    await ownerPage.goto('/settings?tab=security');
    await expect(ownerPage.getByTestId('desktop-idle-timeout')).toBeVisible({ timeout: 20_000 });
    await ownerPage.getByTestId('desktop-idle-timeout').selectOption('15');
    await ownerPage.getByTestId('desktop-idle-timeout-save').click();
    await expect(ownerPage.getByText('Saved')).toBeVisible({ timeout: 15_000 });

    const patched = await ownerPage.request.get('/api/v1/settings');
    expect(patched.ok()).toBeTruthy();
    expect((await patched.json()).desktop_idle_timeout_minutes).toBe(15);

    await ownerPage.evaluate(() => {
      (
        window as Window & {
          __INVSYS_DESKTOP_IDLE__?: { lockNow: () => void };
        }
      ).__INVSYS_DESKTOP_IDLE__?.lockNow();
    });
    await expect(ownerPage.getByTestId('desktop-lock-overlay')).toBeVisible({ timeout: 10_000 });
    const password = ownerPage.getByTestId('desktop-unlock-password');
    await expect(password).toBeVisible({ timeout: 10_000 });
    await password.fill(process.env.E2E_DEMO_PASSWORD ?? 'password123');
    await ownerPage.getByTestId('desktop-unlock-submit').click();
    await expect(ownerPage.getByTestId('desktop-lock-overlay')).toHaveCount(0);
  });
});
