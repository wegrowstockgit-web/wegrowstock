import { expect, test } from '../fixtures/roleFixture';

/**
 * Journey 31 — Enterprise ProductVariant dimensions + advanced columns (default hidden).
 */
test.describe('Journey 31: Enterprise Product Schema', () => {
  test.setTimeout(180_000);

  test('create product with required dims; advanced columns start hidden', async ({
    ownerPage: page,
  }) => {
    const sku = `ENT-${Date.now().toString(36).toUpperCase()}`;

    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
      timeout: 30_000,
    });

    await page.getByRole('button', { name: 'Add product' }).first().click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Add product' })).toBeVisible();

    await dialog.getByLabel('Product name').fill(`Enterprise ${sku}`);
    await dialog.getByLabel('SKU').fill(sku);
    await dialog.getByLabel('Length').fill('12');
    await dialog.getByLabel('Width').fill('8');
    await dialog.getByLabel('Height').fill('4');
    await dialog.getByLabel('Weight', { exact: true }).fill('2.5');

    await dialog.getByText('Trade & Compliance').click();
    await dialog.getByLabel('HS tariff code').fill('8471.30');
    await dialog.getByLabel('Country of origin').fill('US');

    await dialog.getByText('Advanced Handling').click();
    await dialog.getByLabel('Pallet tie (Ti)').fill('10');
    await dialog.getByLabel('Pallet high (Hi)').fill('4');

    const createResp = page.waitForResponse(
      (r) =>
        r.url().includes('/api/v1/variants') &&
        r.request().method() === 'POST' &&
        r.status() < 500,
      { timeout: 30_000 },
    );
    await dialog.getByRole('button', { name: 'Add product' }).click();
    const variantRes = await createResp;
    expect(variantRes.ok(), `variant create failed: ${variantRes.status()}`).toBeTruthy();
    await expect(dialog).toHaveCount(0, { timeout: 15_000 });

    await page.getByPlaceholder('Filter by SKU or name...').fill(sku);
    await expect(page.getByText(sku).first()).toBeVisible({ timeout: 30_000 });

    // Advanced enterprise columns remain hidden until toggled via Columns menu.
    await expect(page.locator('th[data-column-id="hsTariffCode"]')).toHaveCount(0);
    await expect(page.locator('th[data-column-id="isHazmat"]')).toHaveCount(0);
    await expect(page.locator('th[data-column-id="palletTiHi"]')).toHaveCount(0);

    await page.getByTestId('column-visibility-toggle').click();
    await page.getByTestId('column-visibility-hsTariffCode').click();
    await page.keyboard.press('Escape');
    await expect(page.locator('th[data-column-id="hsTariffCode"]')).toBeVisible();
  });
});
