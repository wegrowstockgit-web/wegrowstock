import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

type LedgerAccount = {
  accountId: string;
  name: string;
  type: string;
  classification: string;
  code: string;
};

/**
 * Journey 69 — OAuth-ready accounting wizard: CoA fetch, auto-map, test sync.
 */
test.describe('Journey 69: Integration mapping wizard', () => {
  test('owner walks the QuickBooks wizard against live APIs', async ({ browser }) => {
    test.setTimeout(180_000);
    const owner = await contextForRole(browser, 'owner');
    try {
      const accountsRes = await owner.page.request.get(
        '/api/v1/integrations/accounting/accounts?provider=QUICKBOOKS',
      );
      test.skip(accountsRes.status() === 404, 'Chart-of-accounts API not deployed — rebuild APIs');
      expect(accountsRes.ok(), await accountsRes.text()).toBeTruthy();
      const accounts = (await accountsRes.json()) as LedgerAccount[];
      expect(accounts.some((account) => account.code === '12000')).toBeTruthy();

      const authRes = await owner.page.request.get('/api/v1/integrations/QUICKBOOKS/auth-url');
      expect(authRes.ok(), await authRes.text()).toBeTruthy();
      const auth = (await authRes.json()) as { authorizationUrl: string; state: string };
      expect(auth.authorizationUrl).toContain('intuit.com');
      expect(auth.state).toBeTruthy();

      const statusRes = await owner.page.request.get('/api/v1/integrations/QUICKBOOKS/status');
      expect(statusRes.ok()).toBeTruthy();
      const status = (await statusRes.json()) as {
        connected: boolean;
        accountName: string;
        lastSyncAt: string;
        tokenExpiringSoon: boolean;
      };
      expect(status).toHaveProperty('connected');
      expect(status).toHaveProperty('tokenExpiringSoon');

      const provisionRes = await owner.page.request.post(
        '/api/v1/integrations/accounting/accounts/auto-provision',
        { data: { provider: 'XERO' } },
      );
      expect(provisionRes.ok(), await provisionRes.text()).toBeTruthy();

      await owner.page.goto('/settings/integrations');
      await expect(owner.page.getByTestId('integrations-hub-page')).toBeVisible({ timeout: 20_000 });
      await owner.page.getByTestId('integration-action-QUICKBOOKS').click();
      await expect(owner.page.getByTestId('integration-wizard')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('wizard-no-account-banner')).toBeVisible();
      await owner.page.getByTestId('wizard-continue-mapping').click();
      await expect(owner.page.getByTestId('wizard-step-mapping')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByLabel('Inventory Asset', { exact: true })).not.toHaveValue('', {
        timeout: 15_000,
      });
      await owner.page.getByTestId('wizard-confirm-mappings').click();
      await expect(owner.page.getByTestId('wizard-step-health')).toBeVisible({ timeout: 15_000 });
      await owner.page.getByTestId('wizard-test-sync').click();
      await expect(owner.page.getByTestId('wizard-message')).toBeVisible({ timeout: 15_000 });
    } finally {
      await owner.close();
    }
  });
});
