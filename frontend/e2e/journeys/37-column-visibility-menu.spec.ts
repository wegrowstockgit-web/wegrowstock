import { completeScannerPin, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

const GRID_LS_KEY = 'invsys-grid-columns';

/**
 * Journey 37 — Columns menu must list every toggle (scrollable) and the
 * products grid must reflow without a gap after pinned identifiers.
 */
test.describe('Journey 37: Column visibility menu & grid reflow', () => {
  test.setTimeout(180_000);

  test('menu scrolls to Lifecycle; toggles reflow table without name→barcode gap', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 720 });
      await owner.page.goto('/products');
      await expect(owner.page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
        timeout: 45_000,
      });
      await completeScannerPin(owner.page);
      await expect(owner.page.getByTestId('virtualized-table')).toBeVisible({ timeout: 20_000 });

      // Baseline order: thumb → sku → name → barcode (no orphan thumb between name/barcode)
      const grid = owner.page.getByTestId('virtualized-table-grid');
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .toMatch(/^thumb,sku,name,barcode/);

      // Table should span most of the scrollport (allow horizontal-scroll overflow layouts).
      const scrollport = owner.page.getByTestId('virtualized-table-scrollport');
      await expect
        .poll(async () =>
          scrollport.evaluate((port) => {
            const table = port.querySelector('[data-testid="virtualized-table-grid"]');
            if (!(table instanceof HTMLElement)) return false;
            const tableW = table.getBoundingClientRect().width;
            const portW = port.clientWidth;
            if (portW <= 0) return false;
            return tableW >= portW * 0.85 || Math.abs(tableW - portW) < 48;
          }),
        )
        .toBeTruthy();

      await completeScannerPin(owner.page);
      await owner.page.getByTestId('column-visibility-toggle').click();
      const menu = owner.page.getByTestId('column-visibility-menu');
      await expect(menu).toBeVisible();

      // Menu itself is viewport-bounded and scrollable (not clipped by the window).
      const menuBox = await menu.boundingBox();
      expect(menuBox).toBeTruthy();
      expect(menuBox!.y + menuBox!.height).toBeLessThanOrEqual(720 + 1);
      await expect
        .poll(async () =>
          menu.evaluate((el) => el.scrollHeight > el.clientHeight + 8 || el.clientHeight > 100),
        )
        .toBeTruthy();

      // Last toggle items must be reachable via scrollIntoView.
      const lifecycle = owner.page.getByTestId('column-visibility-lifecycleStatus');
      await lifecycle.scrollIntoViewIfNeeded();
      await expect(lifecycle).toBeVisible();
      await expect(owner.page.getByTestId('column-visibility-abcClassification')).toBeVisible();

      // Hide Weight (or enable then hide) and confirm header + grid attribute update.
      const weightToggle = owner.page.getByTestId('column-visibility-weight');
      await weightToggle.scrollIntoViewIfNeeded();
      const weightWasChecked = await weightToggle.getAttribute('data-state');
      if (weightWasChecked !== 'checked') {
        await weightToggle.click();
        await expect(owner.page.locator('th[data-column-id="weight"]')).toBeVisible();
      }
      await weightToggle.click();
      await expect(owner.page.locator('th[data-column-id="weight"]')).toHaveCount(0);

      // Hide barcode — table width shrinks; pin order stays thumb,sku,name then next cols.
      const barcodeToggle = owner.page.getByTestId('column-visibility-barcode');
      await barcodeToggle.scrollIntoViewIfNeeded();
      await barcodeToggle.click();
      await expect(owner.page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .toMatch(/^thumb,sku,name,/);
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .not.toContain('barcode');

      // Name and next visible header are adjacent (no large empty sticky gap).
      const nameBox = await owner.page.locator('th[data-column-id="name"]').boundingBox();
      const nextId = await grid.evaluate((el) => {
        const cols = (el.getAttribute('data-visible-columns') ?? '').split(',');
        return cols[3] ?? '';
      });
      expect(nextId).toBeTruthy();
      const nextBox = await owner.page.locator(`th[data-column-id="${nextId}"]`).boundingBox();
      expect(nameBox).toBeTruthy();
      expect(nextBox).toBeTruthy();
      const gap = nextBox!.x - (nameBox!.x + nameBox!.width);
      expect(gap, 'columns should sit flush after toggle').toBeLessThan(4);

      // Restore barcode; header returns and order stays coherent.
      await barcodeToggle.click();
      await expect(owner.page.locator('th[data-column-id="barcode"]')).toBeVisible();
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .toMatch(/^thumb,sku,name,barcode/);

      await owner.page.keyboard.press('Escape');

      const stored = await owner.page.evaluate((key) => localStorage.getItem(key), GRID_LS_KEY);
      expect(stored).toBeTruthy();
      const parsed = JSON.parse(stored!) as {
        state?: { layouts?: { products?: { columnVisibility?: Record<string, boolean> } } };
      };
      expect(parsed.state?.layouts?.products?.columnVisibility?.weight).toBe(false);
    } finally {
      await owner.close();
    }
  });
});
