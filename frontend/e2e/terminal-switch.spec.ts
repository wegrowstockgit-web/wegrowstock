import { expect, sessionAccessToken, test } from './fixtures/roleFixture';

test.describe('Terminal PIN context switch', () => {
  test('warehouse surface exposes PIN pad and keeps station session', async ({ ownerPage }) => {
    await ownerPage.goto('/fulfillment');
    await expect(ownerPage.getByText('Floor ops')).toBeVisible();
    await expect(ownerPage.getByTestId('terminal-switch-open')).toBeVisible();

    const token = await sessionAccessToken(ownerPage);
    // Seed a PIN for the signed-in owner so the pad can switch (self).
    const pinRes = await ownerPage.request.post('/api/v1/auth/terminal-pin', {
      headers: { Authorization: `Bearer ${token}` },
      data: { pin: '2468' },
    });
    expect(pinRes.ok()).toBeTruthy();

    await ownerPage.getByTestId('terminal-switch-open').click();
    await expect(ownerPage.getByTestId('terminal-pin-pad')).toBeVisible();

    for (const digit of ['2', '4', '6', '8']) {
      await ownerPage.getByTestId('terminal-pin-keys').locator(`[data-pin-key="${digit}"]`).click();
    }

    await expect(ownerPage.getByTestId('terminal-restore-primary')).toBeVisible({ timeout: 10_000 });
    await ownerPage.getByTestId('terminal-restore-primary').click();
    await expect(ownerPage.getByTestId('terminal-switch-open')).toBeVisible();
  });
});
