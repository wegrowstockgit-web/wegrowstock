import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole, freshLogin } from './helpers';

test.describe('Retail POS settings panel', () => {
  test.setTimeout(120_000);

  test('owner with RETAIL_POS saves receipt and compliance settings', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await owner.page.goto('/settings?tab=retailPos');
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);

      await expect(owner.page.getByTestId('settings-tab-retailPos')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('pos-settings-panel')).toBeVisible({ timeout: 15_000 });
      await expect(owner.page.getByTestId('pos-settings-localization')).toBeVisible();
      await expect(owner.page.getByTestId('pos-settings-receipt')).toBeVisible();
      await expect(owner.page.getByTestId('pos-settings-security')).toBeVisible();

      await owner.page.getByTestId('pos-default-currency').selectOption('MXN');
      const cfdi = owner.page.getByTestId('pos-enable-cfdi');
      if ((await cfdi.getAttribute('aria-checked')) !== 'true') {
        await cfdi.click();
      }
      await owner.page.getByTestId('pos-receipt-header').fill('Demo Corp\nRFC DEM010101AAA');
      await owner.page.getByTestId('pos-receipt-footer').fill('Cambios en 14 dias');
      const blind = owner.page.getByTestId('pos-require-blind-closeout');
      if ((await blind.getAttribute('aria-checked')) !== 'true') {
        await blind.click();
      }
      await owner.page.getByTestId('pos-settings-save').click();
      await expect(owner.page.getByText(/saved|Retail POS settings saved/i).first()).toBeVisible({
        timeout: 15_000,
      });

      await owner.page.reload();
      await completeScannerPin(owner.page);
      await dismissOnboardingTourIfPresent(owner.page);
      await expect(owner.page.getByTestId('pos-default-currency')).toHaveValue('MXN', { timeout: 15_000 });
      await expect(owner.page.getByTestId('pos-enable-cfdi')).toHaveAttribute('aria-checked', 'true');
      await expect(owner.page.getByTestId('pos-receipt-header')).toHaveValue(/RFC DEM010101AAA/);
      await expect(owner.page.getByTestId('pos-receipt-footer')).toHaveValue('Cambios en 14 dias');
      await expect(owner.page.getByTestId('pos-require-blind-closeout')).toHaveAttribute(
        'aria-checked',
        'true',
      );
    } finally {
      await owner.close();
    }
  });

  test('warehouse manager cannot open the settings hub or POS panel', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.setViewportSize({ width: 1280, height: 800 });
      await manager.page.goto('/settings?tab=retailPos');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await expect(manager.page.getByTestId('pos-settings-panel')).toHaveCount(0);
      await expect(manager.page.getByTestId('settings-tab-retailPos')).toHaveCount(0);
      await expect(manager.page).not.toHaveURL(/\/settings(\?|$)/);
    } finally {
      await manager.close();
    }
  });

  test('owner without RETAIL_POS does not see the Retail POS tab', async ({ browser }) => {
    const acme = await freshLogin(browser, 'owner@acme.test');
    try {
      await acme.page.setViewportSize({ width: 1280, height: 800 });
      await acme.page.goto('/settings?tab=retailPos');
      await completeScannerPin(acme.page);
      await dismissOnboardingTourIfPresent(acme.page);

      await expect(acme.page.getByTestId('settings-nav')).toBeVisible({ timeout: 15_000 });
      await expect(acme.page.getByTestId('settings-tab-retailPos')).toHaveCount(0);
      await expect(acme.page.getByTestId('pos-settings-panel')).toHaveCount(0);

      await acme.page.goto('/settings/pos');
      await completeScannerPin(acme.page);
      await expect(acme.page).toHaveURL(/\/upgrade/, { timeout: 15_000 });
      await expect(acme.page.getByTestId('upgrade-page')).toBeVisible();
    } finally {
      await acme.close();
    }
  });
});
