import { test } from '@playwright/test';
import { apiJson, contextForRole, expect, unwrapItems } from './helpers';

test.describe('Journey 79: Supplier list workspace routing', () => {
  test.setTimeout(180_000);

  test('name link and Open Workspace go to the workspace without a peek drawer', async ({
    browser,
  }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const suppliers = unwrapItems<{ id: string; name: string }>(
        await apiJson(manager.page, '/api/v1/suppliers?page=1&size=100'),
      );
      const supplier = suppliers[0];
      if (!supplier) {
        throw new Error('No suppliers available for workspace routing');
      }

      await manager.page.goto('/suppliers');
      await expect(manager.page.getByTestId('suppliers-page')).toBeVisible({ timeout: 20_000 });
      const skipTour = manager.page.getByRole('button', { name: 'Not now' });
      if (await skipTour.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await skipTour.click();
      }

      await manager.page.getByTestId('table-search').fill(supplier.name);
      const nameLink = manager.page.getByTestId(`supplier-name-link-${supplier.id}`);
      await expect(nameLink).toBeVisible({ timeout: 20_000 });
      await expect(nameLink).toHaveAttribute('href', `/purchasing/suppliers/${supplier.id}`);
      await expect(manager.page.getByTestId('right-peek-drawer')).toHaveCount(0);
      await expect(manager.page.getByTestId('open-supplier-workspace')).toHaveCount(0);

      await nameLink.click();
      await expect(manager.page).toHaveURL(new RegExp(`/purchasing/suppliers/${supplier.id}`));
      await expect(manager.page.getByTestId('supplier-workspace')).toBeVisible();
      await expect(manager.page.getByTestId('right-peek-drawer')).toHaveCount(0);

      await manager.page.goto('/suppliers');
      await expect(manager.page.getByTestId('suppliers-page')).toBeVisible({ timeout: 20_000 });
      await manager.page.getByTestId('table-search').fill(supplier.name);
      await expect(manager.page.getByTestId(`supplier-name-link-${supplier.id}`)).toBeVisible({
        timeout: 20_000,
      });
      await manager.page
        .getByTestId(`supplier-row-${supplier.id}`)
        .getByTestId('supplier-row-actions')
        .click();
      await manager.page.getByTestId('supplier-open-workspace').click();
      await expect(manager.page).toHaveURL(new RegExp(`/purchasing/suppliers/${supplier.id}`));
      await expect(manager.page.getByTestId('supplier-workspace')).toBeVisible();
    } finally {
      await manager.close();
    }
  });
});
