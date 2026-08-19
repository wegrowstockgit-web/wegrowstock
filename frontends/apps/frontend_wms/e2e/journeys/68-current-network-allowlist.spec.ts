import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

type NetworkInfo = {
  clientIp: string;
  suggestedCidr: string;
  isPrivateNetwork: boolean;
  networkHint: string;
};

type MatrixResponse = {
  allowedCidrBlocks?: string[];
};

/**
 * Auto-detect the owner's current IP and one-click add it to the corporate allowlist.
 */
test.describe('Journey 68: Current network auto-detect', () => {
  test('owner sees current connection and adds it to the allowlist', async ({ browser }) => {
    test.setTimeout(180_000);
    const owner = await contextForRole(browser, 'owner');
    let originalCidrs: string[] = [];
    try {
      const infoRes = await owner.page.request.get('/api/v1/settings/network/current-ip');
      test.skip(infoRes.status() === 404, 'Current-IP API not deployed — rebuild API');
      expect(infoRes.ok(), await infoRes.text()).toBeTruthy();
      const info = (await infoRes.json()) as NetworkInfo;
      expect(info.clientIp).toBeTruthy();
      expect(info.suggestedCidr).toMatch(/\/(32|128)$/);
      expect(info.networkHint).toBeTruthy();

      const matrixRes = await owner.page.request.get('/api/v1/settings/permissions');
      expect(matrixRes.ok()).toBeTruthy();
      originalCidrs = [...(((await matrixRes.json()) as MatrixResponse).allowedCidrBlocks ?? [])];

      await owner.page.goto('/settings?tab=users');
      await expect(owner.page.getByTestId('corporate-ip-allowlist')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('current-network-banner')).toBeVisible();
      await expect(owner.page.getByTestId('current-network-ip')).toHaveText(info.clientIp);

      const add = owner.page.getByTestId('add-current-network');
      if (await add.isEnabled()) {
        const patched = owner.page.waitForResponse(
          (res) =>
            res.url().includes('/api/v1/settings/permissions/allowed-cidrs') &&
            res.request().method() === 'PATCH' &&
            res.ok(),
        );
        await add.click();
        await patched;
        await expect(owner.page.getByTestId('app-toast')).toContainText(
          `Added your current network (${info.clientIp})`,
          { timeout: 10_000 },
        );
      }

      await expect(owner.page.getByTestId(`cidr-chip-${info.suggestedCidr}`)).toBeVisible({
        timeout: 10_000,
      });
    } finally {
      await owner.page.request.patch('/api/v1/settings/permissions/allowed-cidrs', {
        data: { allowedCidrBlocks: originalCidrs },
      });
      await owner.close();
    }
  });
});
