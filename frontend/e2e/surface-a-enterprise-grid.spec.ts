import { expect, test } from './fixtures/roleFixture';

test.describe('Surface A enterprise grid & settings', () => {
  test('products density toggle and column visibility menu', async ({ ownerPage: page }) => {
    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Products' })).toBeVisible({ timeout: 20_000 });

    const density = page.getByTestId('density-toggle');
    await expect(density).toBeVisible();
    await density.click();
    await page.getByTestId('density-option-compact').click();
    await expect(density).toContainText(/Compact/i);

    const columns = page.getByTestId('column-visibility-toggle');
    await expect(columns).toBeVisible();
    await columns.click();
    await expect(page.getByText('Toggle columns')).toBeVisible();
    await page.keyboard.press('Escape');

    await expect(page.getByTestId('virtualized-table').or(page.getByText(/No products yet/i))).toBeVisible();
  });

  test('import wizard page loads with mapping workspace', async ({ ownerPage: page }) => {
    await page.goto('/import');
    await expect(page.getByRole('heading', { name: 'Data import' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Column mapping/i)).toBeVisible();
    await expect(page.getByText(/Drop a CSV here/i)).toBeVisible();
  });

  test('warehouse visualizer and stacked tax settings', async ({ ownerPage: page }) => {
    await page.goto('/settings');
    await expect(page.getByRole('button', { name: 'Warehouses' })).toBeVisible({ timeout: 15_000 });

    await page.getByRole('button', { name: 'Warehouses' }).click();
    await expect(page.getByTestId('warehouse-visualizer')).toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByText(/Spatial hierarchy|Place your first warehouse|Click a cell/i).first(),
    ).toBeVisible();

    await page.getByRole('button', { name: 'Inventory Rules' }).click();
    await expect(page.getByLabel('Costing method')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Stacked tax schemes' })).toBeVisible();

    await page.getByRole('button', { name: 'Documents' }).click();
    await expect(page.getByRole('heading', { name: /SKU & barcode masks/i })).toBeVisible();
    await expect(page.getByLabel('SKU template')).toBeVisible();
  });
});
