import { completeScannerPin, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

const GRID_LS_KEY = 'invsys-grid-columns';

/**
 * Journey 18 — Product Master grid customization (local Zustand persist only).
 */
test.describe('Journey 18: Grid Customization & Channel Sync', () => {
  test.setTimeout(180_000);

  test('local layout persist; columns menu actions; channel sync PATCH', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    const page = owner.page;
    try {
      await page.goto('/products');
      await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
        timeout: 45_000,
      });
      await completeScannerPin(page);
      await expect(page.getByTestId('data-list-toolbar')).toBeVisible();
      // Server Save View removed — layout is local persist only
      await expect(page.getByTestId('save-view-button')).toHaveCount(0);

      // --- Columns menu: hide Barcode, pin Reorder ---
      await completeScannerPin(page);
      await page.getByTestId('column-visibility-toggle').click();
      await expect(page.getByTestId('column-visibility-menu')).toBeVisible();
      await page.getByTestId('column-visibility-barcode').click();
      await page.getByTestId('column-pin-reorder').evaluate((el) => (el as HTMLElement).click());
      await page.keyboard.press('Escape');

      await expect(page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
      const reorderHeader = page.locator('th[data-column-id="reorder"]');
      await expect(reorderHeader).toBeVisible();
      await expect(reorderHeader).toHaveAttribute('data-pinned', 'true');
      await expect
        .poll(async () => reorderHeader.evaluate((el) => getComputedStyle(el).position))
        .toBe('sticky');

      // Spot-check other column menu actions
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

      // Reload — localStorage hydration (layouts.products)
      await page.reload({ waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
        timeout: 30_000,
      });

      const stored = await page.evaluate((key) => localStorage.getItem(key), GRID_LS_KEY);
      expect(stored).toBeTruthy();
      const parsed = JSON.parse(stored!) as {
        state?: {
          layouts?: {
            products?: {
              columnVisibility?: Record<string, boolean>;
              pinnedColumns?: string[];
            };
          };
        };
      };
      expect(parsed.state?.layouts?.products?.columnVisibility?.barcode).toBe(false);
      expect(parsed.state?.layouts?.products?.pinnedColumns).toContain('reorder');

      await expect(page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
      await expect(page.locator('th[data-column-id="reorder"]')).toHaveAttribute(
        'data-pinned',
        'true',
      );

      // --- Channel Sync ---
      await completeScannerPin(page);
      const syncBox = page.getByRole('checkbox', { name: 'Channel sync' }).first();
      await expect(syncBox).toBeVisible({ timeout: 20_000 });
      await syncBox.scrollIntoViewIfNeeded();
      const wasChecked = await syncBox.isChecked();
      const patchWait = page.waitForResponse(
        (res) =>
          /\/api\/v1\/variants\/[^/]+\/channel-sync/.test(res.url()) &&
          res.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await syncBox.click({ force: true });
      const patchRes = await patchWait;
      expect(patchRes.status()).toBe(200);
      await expect(syncBox).toBeChecked({ checked: !wasChecked });
    } finally {
      await owner.close();
    }
  });
});
