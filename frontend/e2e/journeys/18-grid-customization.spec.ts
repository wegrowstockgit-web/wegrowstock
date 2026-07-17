import { expect, test } from '../fixtures/roleFixture';

const GRID_LS_KEY = 'invsys-grid-columns';

/**
 * Journey 18 — Product Master grid customization + Channel Sync toggle.
 */
test.describe('Journey 18: Grid Customization & Channel Sync', () => {
  test.setTimeout(180_000);

  test('save purchasing layout; columns menu actions; channel sync PATCH', async ({
    ownerPage: page,
  }) => {
    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByTestId('data-list-toolbar')).toBeVisible();

    // --- Columns menu: hide Barcode, pin Reorder ---
    await page.getByTestId('column-visibility-toggle').click();
    await page.getByTestId('column-visibility-barcode').click();
    // Pin section is below the fold in the Columns menu — DOM click avoids viewport clip
    await page.getByTestId('column-pin-reorder').evaluate((el) => (el as HTMLElement).click());
    await page.keyboard.press('Escape');

    await expect(page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
    const reorderHeader = page.locator('th[data-column-id="reorder"]');
    await expect(reorderHeader).toBeVisible();
    await expect(reorderHeader).toHaveAttribute('data-pinned', 'true');
    await expect
      .poll(async () => reorderHeader.evaluate((el) => getComputedStyle(el).position))
      .toBe('sticky');

    // Spot-check other column menu actions (toggle barcode visibility + pin/unpin On hand)
    await page.getByTestId('column-visibility-toggle').click();
    await page.getByTestId('column-visibility-barcode').click();
    await page.keyboard.press('Escape');
    await expect(page.locator('th[data-column-id="barcode"]')).toBeVisible();
    await page.getByTestId('column-visibility-toggle').click();
    await page.getByTestId('column-visibility-barcode').click();
    await page.getByTestId('column-pin-onHand').evaluate((el) => (el as HTMLElement).click());
    await page.getByTestId('column-pin-onHand').evaluate((el) => (el as HTMLElement).click());
    await page.keyboard.press('Escape');
    await expect(page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
    await expect(page.locator('th[data-column-id="reorder"]')).toHaveAttribute(
      'data-pinned',
      'true',
    );

    // --- Save view ---
    await page.getByTestId('save-view-button').click();
    await page.getByTestId('save-view-name-input').fill('Purchasing Layout');
    const saveWait = page.waitForResponse(
      (res) =>
        res.url().includes('/api/v1/users/me/views') && res.request().method() === 'POST',
      { timeout: 30_000 },
    );
    await page.getByTestId('save-view-confirm').click();
    const saveRes = await saveWait;
    expect(saveRes.ok(), await saveRes.text()).toBeTruthy();

    await page.getByTestId('saved-views-dropdown').click();
    await expect(page.getByTestId('saved-view-Purchasing Layout')).toBeVisible();
    await page.keyboard.press('Escape');

    // Reload — localStorage hydration + server view list
    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
      timeout: 30_000,
    });

    const stored = await page.evaluate((key) => localStorage.getItem(key), GRID_LS_KEY);
    expect(stored).toBeTruthy();
    const parsed = JSON.parse(stored!) as {
      state?: {
        columnVisibility?: Record<string, boolean>;
        pinnedColumns?: string[];
      };
    };
    expect(parsed.state?.columnVisibility?.barcode).toBe(false);
    expect(parsed.state?.pinnedColumns).toContain('reorder');

    await expect(page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
    await expect(page.locator('th[data-column-id="reorder"]')).toHaveAttribute(
      'data-pinned',
      'true',
    );

    await page.getByTestId('saved-views-dropdown').click();
    await expect(page.getByTestId('saved-view-Purchasing Layout')).toBeVisible();
    await page.getByTestId('saved-view-Purchasing Layout').click();
    await expect(page.locator('th[data-column-id="barcode"]')).toHaveCount(0);

    // --- Channel Sync ---
    const syncBox = page.getByRole('checkbox', { name: 'Channel sync' }).first();
    await expect(syncBox).toBeVisible({ timeout: 20_000 });
    const wasChecked = await syncBox.isChecked();
    const patchWait = page.waitForResponse(
      (res) =>
        /\/api\/v1\/variants\/[^/]+\/channel-sync/.test(res.url()) &&
        res.request().method() === 'PATCH',
      { timeout: 30_000 },
    );
    await syncBox.click();
    const patchRes = await patchWait;
    expect(patchRes.status()).toBe(200);
    await expect(syncBox).toBeChecked({ checked: !wasChecked });
  });
});
