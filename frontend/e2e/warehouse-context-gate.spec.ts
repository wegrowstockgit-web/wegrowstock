import { expect, sessionAccessToken, test } from './fixtures/roleFixture';

const WH_01 = 'a0000000-0000-4000-8000-000000000601';
const WH_02 = 'a0000000-0000-4000-8000-000000000611';

test.describe('Warehouse context gate', () => {
  test('JWT single-warehouse kiosk hides switcher on office and floor routes', async ({
    pickerPage,
  }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.locator('[data-terminal-locked="true"]')).toBeVisible();
    await expect(pickerPage.getByLabel('Active warehouse')).toHaveCount(0);

    await pickerPage.goto('/cycle-counts');
    await expect(pickerPage.locator('[data-terminal-locked="true"]')).toBeVisible();
  });

  test('SSID hardware hint auto-locks owner warehouse switcher', async ({ ownerPage }) => {
    await ownerPage.goto('/dashboard');
    await expect(ownerPage.getByLabel('Active warehouse')).toBeVisible({ timeout: 15_000 });

    const token = await sessionAccessToken(ownerPage);

    const create = await ownerPage.request.post('/api/v1/warehouse-context-rules', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        locationId: WH_02,
        matchType: 'WIFI_SSID',
        ssid: 'E2E-Floor-Overflow',
        priority: 5,
        enabled: true,
        label: 'E2E Overflow SSID',
      },
    });
    expect(create.ok()).toBeTruthy();

    await ownerPage.evaluate(() => {
      localStorage.setItem(
        'invsys-network-hint',
        JSON.stringify({ ssid: 'E2E-Floor-Overflow' })
      );
    });

    await ownerPage.goto('/fulfillment');
    await expect(ownerPage.getByText('Floor ops')).toBeVisible();

    await ownerPage.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent('invsys:network-context', {
          detail: { ssid: 'E2E-Floor-Overflow' },
        })
      );
    });

    const locked = ownerPage.locator('[data-terminal-locked="true"]');
    await expect(locked).toBeVisible({ timeout: 15_000 });
    await expect(locked).toHaveAttribute('data-lock-reason', 'HARDWARE_SSID');
    await expect(ownerPage.getByLabel('Active warehouse')).toHaveCount(0);
  });

  test('owner without hardware hint still has warehouse switcher', async ({ ownerPage }) => {
    await ownerPage.goto('/dashboard');
    await ownerPage.evaluate(() => localStorage.removeItem('invsys-network-hint'));
    await ownerPage.reload();
    await expect(ownerPage.getByLabel('Active warehouse')).toBeVisible({ timeout: 15_000 });
    await expect(ownerPage.getByLabel('Active warehouse')).toHaveValue(WH_01);
  });
});
