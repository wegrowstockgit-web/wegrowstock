import { test } from '@playwright/test';
import { contextForRole, DEMO_PASSWORD, expect } from './helpers';

type MatrixResponse = {
  roles: Array<{ id: string; name: string; networkAccessLevel?: string }>;
  allowedCidrBlocks?: string[];
};

/**
 * Conditional access: role network levels, CIDR allowlist UI, and MFA login intercept.
 */
test.describe('Journey 55: Role-based conditional access', () => {
  test('matrix exposes network levels, CIDR editor, and MFA challenge UI', async ({ browser }) => {
    test.setTimeout(180_000);
    const owner = await contextForRole(browser, 'owner');
    let pickerId: string | undefined;
    let originalCidrs: string[] = [];
    let originalLevel = 'STRICT_INTERNAL';
    try {
      const matrixRes = await owner.page.request.get('/api/v1/settings/permissions');
      test.skip(!matrixRes.ok(), 'Permissions API not deployed');
      const matrix = (await matrixRes.json()) as MatrixResponse;
      const picker = matrix.roles.find((r) => r.name === 'PICKER');
      expect(picker, 'PICKER role missing').toBeTruthy();
      expect(picker!.networkAccessLevel).toBeTruthy();
      pickerId = picker!.id;
      originalCidrs = [...(matrix.allowedCidrBlocks ?? [])];
      originalLevel = picker!.networkAccessLevel ?? 'STRICT_INTERNAL';

      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('role-permissions-matrix')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('corporate-ip-allowlist')).toBeVisible();
      await expect(owner.page.getByTestId('network-access-PICKER')).toBeVisible();

      const patchLevel = await owner.page.request.patch('/api/v1/settings/permissions/network-access', {
        data: { roleId: picker!.id, networkAccessLevel: 'ROAMING' },
      });
      expect(patchLevel.ok(), await patchLevel.text()).toBeTruthy();
      const patched = (await (await owner.page.request.get('/api/v1/settings/permissions')).json()) as MatrixResponse;
      expect(patched.roles.find((r) => r.name === 'PICKER')?.networkAccessLevel).toBe('ROAMING');

      // Include IPv4-any plus RFC1918 so reload does not fence the owner session.
      const previewCidrs = ['10.0.0.0/8', '0.0.0.0/0', '127.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'];
      const patchCidr = await owner.page.request.patch('/api/v1/settings/permissions/allowed-cidrs', {
        data: { allowedCidrBlocks: previewCidrs },
      });
      expect(patchCidr.ok(), await patchCidr.text()).toBeTruthy();

      await owner.page.reload();
      await expect(owner.page.getByTestId('cidr-chip-10.0.0.0/8')).toBeVisible({ timeout: 15_000 });
    } finally {
      if (pickerId) {
        await owner.page.request.patch('/api/v1/settings/permissions/network-access', {
          data: { roleId: pickerId, networkAccessLevel: originalLevel },
        });
      }
      await owner.page.request.patch('/api/v1/settings/permissions/allowed-cidrs', {
        data: { allowedCidrBlocks: originalCidrs },
      });
      await owner.close();
    }

    const page = await browser.newPage({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });
    try {
      await page.addInitScript(() => {
        const credentials = {
          get: async () => {
            throw new DOMException('NotAllowedError', 'NotAllowedError');
          },
          create: async () => {
            throw new DOMException('NotAllowedError', 'NotAllowedError');
          },
        };
        Object.defineProperty(navigator, 'credentials', {
          configurable: true,
          value: credentials,
        });
        localStorage.setItem(
          'invsys.terminalPasskey',
          JSON.stringify({
            credentialId: 'cred_1',
            secret: 'secret',
            userId: 'u1',
            tenantId: 't1',
          }),
        );
      });
      await page.route('**/api/v1/**', async (route) => {
        const url = route.request().url();
        const method = route.request().method();
        if (url.includes('/api/v1/auth/discovery')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ isPasswordAllowed: true, companyName: 'Fence Co' }),
          });
          return;
        }
        if (url.includes('/api/v1/auth/login') && method === 'POST') {
          const data = route.request().postDataJSON() as { mfaSignature?: string };
          if (!data?.mfaSignature) {
            await route.fulfill({
              status: 401,
              contentType: 'application/problem+json',
              body: JSON.stringify({
                title: 'MFA_REQUIRED_FOR_EXTERNAL_ACCESS',
                challenge: 'chal',
                allowCredentials: [{ id: 'cred_1' }],
              }),
            });
            return;
          }
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              tenantId: 't1',
              userId: 'u1',
              roles: ['OWNER'],
              warehouseIds: [],
              grantedPermissions: [],
            }),
          });
          return;
        }
        if (url.includes('/api/v1/auth/me')) {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              userId: 'u1',
              tenantId: 't1',
              email: 'owner@demo.test',
              displayName: 'Owner',
              roles: ['OWNER'],
            }),
          });
          return;
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      });

      await page.goto('/login');
      await page.getByTestId('login-email').fill('owner@demo.test');
      await page.getByTestId('login-continue').click();
      await expect(page.getByTestId('login-password')).toBeVisible();
      await page.getByTestId('login-password').fill(DEMO_PASSWORD);
      await page.getByTestId('login-submit').click();
      await expect(page.getByTestId('login-mfa-challenge')).toBeVisible();
      await page.getByTestId('login-mfa-submit').click();
      await expect(page).toHaveURL(/dashboard/, { timeout: 15_000 });
      const persisted = await page.evaluate(() => localStorage.getItem('invsys-session'));
      expect(persisted, 'session store missing').toBeTruthy();
      expect(JSON.parse(persisted as string).state.mfaVerified).toBe(true);
    } finally {
      await page.close();
    }
  });
});
