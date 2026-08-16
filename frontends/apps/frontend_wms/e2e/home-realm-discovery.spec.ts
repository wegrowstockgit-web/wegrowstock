import { expect, test } from '@playwright/test';
import { completeIdentifierFirstLogin, loginAsDemo } from './fixtures/roleFixture';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';

test.describe('Home Realm Discovery (identifier-first login)', () => {
  test('step 1 is email-only; demo email reveals password + magic link', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('login-step-email')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('login-email')).toBeVisible();
    await expect(page.getByTestId('login-continue')).toBeVisible();
    await expect(page.getByTestId('login-password')).toHaveCount(0);
    await expect(page.getByTestId('login-magic-link')).toHaveCount(0);
    await expect(page.getByTestId('login-sso-primary')).toHaveCount(0);

    const discovery = page.waitForResponse(
      (res) => res.url().includes('/api/v1/auth/discovery') && res.request().method() === 'GET',
      { timeout: 20_000 },
    );
    await page.getByTestId('login-continue').click();
    const res = await discovery;
    expect(res.ok()).toBeTruthy();
    const body = (await res.json()) as { isPasswordAllowed: boolean; ssoUrl?: string | null };
    expect(body.isPasswordAllowed).toBe(true);
    expect(body.ssoUrl ?? null).toBeNull();

    await expect(page.getByTestId('login-step-password')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('login-password')).toBeVisible();
    await expect(page.getByTestId('login-submit')).toBeVisible();
    await expect(page.getByTestId('login-magic-link')).toBeVisible();
    await expect(page.getByTestId('login-change-email')).toBeVisible();
  });

  test('public discovery API is password-only for demo.test', async ({ request }) => {
    const res = await request.get('/api/v1/auth/discovery', {
      params: { email: 'owner@demo.test' },
    });
    expect(res.ok()).toBeTruthy();
    const body = (await res.json()) as {
      isPasswordAllowed: boolean;
      ssoUrl?: string | null;
      tenantId?: string | null;
    };
    expect(body.isPasswordAllowed).toBe(true);
    expect(body.ssoUrl ?? null).toBeNull();
    expect(body.tenantId ?? null).toBeNull();
  });

  test('identifier-first UI login reaches the office dashboard', async ({ page }) => {
    await page.goto('/login');
    await completeIdentifierFirstLogin(page, 'owner@demo.test', DEMO_PASSWORD);
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 45_000 });
  });

  test('Settings security tab exposes domain, SSO, and CIDR self-service', async ({ page }) => {
    await loginAsDemo(page, 'owner@demo.test', DEMO_PASSWORD);
    await page.goto('/settings?tab=security');
    await expect(page.getByTestId('security-sso-tab')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('hrd-domain-input')).toBeVisible();
    await expect(page.getByTestId('hrd-domain-add')).toBeVisible();
    await expect(page.getByTestId('hrd-cidr-input')).toBeVisible();
    await expect(page.getByTestId('sso-enforce')).toBeVisible();
    await expect(page.getByTestId('sso-card-GOOGLE')).toBeVisible();
    await expect(page.getByTestId('sso-card-ENTRA')).toBeVisible();
    await expect(page.getByTestId('sso-card-OKTA')).toBeVisible();

    const domain = `hrd-${Date.now().toString(36)}.example`;
    await page.getByTestId('hrd-domain-input').fill(domain);
    const registerWait = page.waitForResponse(
      (res) =>
        res.url().includes('/api/v1/settings/email-domains') && res.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await page.getByTestId('hrd-domain-add').click();
    expect((await registerWait).ok()).toBeTruthy();
    await expect(page.getByTestId(`hrd-domain-${domain}`)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/growstock-verification=/)).toBeVisible();

    await page.getByTestId('hrd-cidr-input').fill('203.0.113.0/24');
    await page.getByTestId('hrd-cidr-add').click();
    await expect(page.getByTestId('hrd-cidr-list')).toContainText('203.0.113.0/24');
  });
});
