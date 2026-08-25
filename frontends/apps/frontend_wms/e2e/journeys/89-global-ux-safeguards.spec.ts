import { dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 89 — Global UX safeguards: opaque header, constrained products workspace,
 * and interceptor-backed problem toast on a 409 (real Add product submit).
 */
test.describe('Journey 89: Global UX enforcement', () => {
  test.setTimeout(180_000);

  test('products workspace is constrained; header is opaque; 409 detail toasts', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.route('**/api/v1/products**', async (route) => {
        const method = route.request().method();
        const path = new URL(route.request().url()).pathname.replace(/\/$/, '');
        if (method === 'POST' && path.endsWith('/api/v1/products')) {
          await route.fulfill({
            status: 409,
            contentType: 'application/problem+json',
            body: JSON.stringify({
              type: 'about:blank',
              title: 'Conflict',
              status: 409,
              detail: 'Bin capacity exceeded',
            }),
          });
          return;
        }
        await route.continue();
      });

      await owner.page.goto('/products');
      await dismissOnboardingTourIfPresent(owner.page);
      await expect(owner.page.getByTestId('products-grid-shell')).toBeVisible({ timeout: 30_000 });

      const shell = owner.page.getByTestId('products-grid-shell');
      await expect(shell).toHaveClass(/min-h-0/);
      await expect(shell).toHaveClass(/flex-1/);
      await expect(owner.page.getByTestId('data-list-workspace-content')).toHaveClass(/min-h-0/);

      const header = owner.page.getByTestId('app-header');
      await expect(header).toBeVisible();
      const headerClass = (await header.getAttribute('class')) ?? '';
      expect(headerClass).toContain('bg-surface-raised');
      expect(headerClass).not.toContain('backdrop-blur');
      expect(headerClass).not.toMatch(/bg-surface-raised\//);

      await owner.page.getByRole('button', { name: 'Add product' }).first().click();
      const dialog = owner.page.getByRole('dialog');
      await expect(dialog.getByRole('heading', { name: 'Add product' })).toBeVisible();

      const sku = `UX-${Date.now().toString(36).toUpperCase()}`;
      await dialog.getByLabel('Product name').fill(`Safeguard ${sku}`);
      await dialog.getByLabel('SKU').fill(sku);
      await dialog.getByLabel('Length').fill('12');
      await dialog.getByLabel('Width').fill('8');
      await dialog.getByLabel('Height').fill('4');
      await dialog.getByLabel('Weight', { exact: true }).fill('2.5');

      const conflict = owner.page.waitForResponse(
        (r) =>
          r.request().method() === 'POST' &&
          new URL(r.url()).pathname.replace(/\/$/, '').endsWith('/api/v1/products') &&
          r.status() === 409,
        { timeout: 15_000 },
      );
      await dialog.getByRole('button', { name: 'Add product' }).click();
      await conflict;

      await expect(owner.page.getByTestId('app-toast')).toContainText('Bin capacity exceeded', {
        timeout: 10_000,
      });
      await expect(dialog.getByText(/Could not create product/i)).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
