import { expect, sessionAccessToken, test } from './fixtures/roleFixture';

test.describe('Terminal biometric context switch', () => {
  test('passkey assert switches operator without killing station session', async ({ ownerPage }) => {
    await ownerPage.goto('/fulfillment');
    await expect(ownerPage.getByText('Floor ops')).toBeVisible();

    const token = await sessionAccessToken(ownerPage);
    const users = await ownerPage.request.get('/api/v1/users', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(users.ok()).toBeTruthy();
    const list = (await users.json()) as Array<{ id: string; email: string }>;
    const owner = list[0];
    expect(owner?.id).toBeTruthy();

    const reg = await ownerPage.request.post(`/api/v1/users/${owner.id}/terminal-passkey`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { label: 'E2E glove key' },
    });
    expect(reg.ok()).toBeTruthy();
    const body = (await reg.json()) as { credentialId: string; secret: string };

    await ownerPage.evaluate(
      ({ credentialId, secret }) => {
        localStorage.setItem(
          'invsys.terminalPasskey',
          JSON.stringify({ credentialId, secret })
        );
      },
      { credentialId: body.credentialId, secret: body.secret }
    );

    await ownerPage.getByTestId('terminal-switch-open').click();
    await expect(ownerPage.getByTestId('terminal-pin-pad')).toBeVisible();
    await ownerPage.getByTestId('terminal-biometric-assert').click();
    await expect(ownerPage.getByTestId('terminal-restore-primary')).toBeVisible({ timeout: 10_000 });
    await ownerPage.getByTestId('terminal-restore-primary').click();
    await expect(ownerPage.getByTestId('terminal-switch-open')).toBeVisible();
  });
});
