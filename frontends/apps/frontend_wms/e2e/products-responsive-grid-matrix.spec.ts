import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from './fixtures/roleFixture';
import { contextForRole } from './journeys/helpers';

const GRID_LS_KEY = 'invsys-grid-columns';

/**
 * Interactive matrix: play with Columns menu, horizontal scroll, pinning,
 * and breakpoint layouts (desktop / tablet / mobile) on /products.
 */
test.describe('Products responsive grid matrix', () => {
  test.setTimeout(240_000);

  async function openProducts(page: import('@playwright/test').Page, width: number, height: number) {
    await page.setViewportSize({ width, height });
    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
      timeout: 45_000,
    });
    await completeScannerPin(page);
    await dismissOnboardingTourIfPresent(page);
  }

  async function showAllHideableColumns(page: import('@playwright/test').Page) {
    await page.getByTestId('column-visibility-toggle').click();
    const menu = page.getByTestId('column-visibility-menu');
    await expect(menu).toBeVisible({ timeout: 10_000 });

    const toggles = menu.locator('[data-testid^="column-visibility-"]');
    const count = await toggles.count();
    for (let i = 0; i < count; i++) {
      const toggle = toggles.nth(i);
      const state = await toggle.getAttribute('data-state');
      if (state !== 'checked') {
        await toggle.scrollIntoViewIfNeeded();
        await toggle.click();
      }
    }
    await page.keyboard.press('Escape');
    await expect(menu).toHaveCount(0);
  }

  test('desktop: show all columns, H-scroll under sticky SKU/Name, no name canyon', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await openProducts(owner.page, 1440, 900);
      const shell = owner.page.getByTestId('products-grid-shell');
      await expect(shell).toHaveAttribute('data-layout', 'desktop');
      await expect(owner.page.getByTestId('virtualized-table')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('products-mobile-list')).toHaveCount(0);

      await showAllHideableColumns(owner.page);

      const grid = owner.page.getByTestId('virtualized-table-grid');
      const scrollport = owner.page.getByTestId('virtualized-table-scrollport');
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .toMatch(/thumb,sku,name,barcode/);
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .toMatch(/hsTariffCode|lifecycleStatus|abcClassification/);

      // Sticky freeze on identity columns.
      await expect(owner.page.locator('th[data-column-id="sku"]')).toHaveAttribute(
        'data-pinned',
        'true',
      );
      await expect(owner.page.locator('th[data-column-id="name"]')).toHaveAttribute(
        'data-pinned',
        'true',
      );

      // Name stays bounded — no canyon (maxWidth 280).
      const nameBox = await owner.page.locator('th[data-column-id="name"]').boundingBox();
      expect(nameBox).toBeTruthy();
      expect(nameBox!.width).toBeLessThanOrEqual(300);

      const onHandBox = await owner.page.locator('th[data-column-id="onHand"]').boundingBox();
      expect(onHandBox).toBeTruthy();
      // Ops metrics sit after identity without a multi-hundred-px sticky void.
      expect(onHandBox!.x - (nameBox!.x + nameBox!.width)).toBeLessThan(400);

      // Horizontal scroll when all columns are visible.
      await expect
        .poll(async () =>
          scrollport.evaluate((el) => el.scrollWidth > el.clientWidth + 8),
        )
        .toBeTruthy();

      const beforeSkuX = (await owner.page.locator('th[data-column-id="sku"]').boundingBox())!.x;
      await scrollport.evaluate((el) => {
        el.scrollLeft = Math.min(el.scrollWidth, 280);
      });
      await expect
        .poll(async () => scrollport.evaluate((el) => el.scrollLeft))
        .toBeGreaterThan(40);

      // Pinned SKU stays put while unpinned headers slide.
      const afterSkuX = (await owner.page.locator('th[data-column-id="sku"]').boundingBox())!.x;
      expect(Math.abs(afterSkuX - beforeSkuX)).toBeLessThan(2);

      const barcodeBefore = await owner.page
        .locator('th[data-column-id="barcode"]')
        .evaluate((el) => el.getBoundingClientRect().x);
      await scrollport.evaluate((el) => {
        el.scrollLeft = 0;
      });
      const barcodeAfter = await owner.page
        .locator('th[data-column-id="barcode"]')
        .evaluate((el) => el.getBoundingClientRect().x);
      expect(barcodeAfter).toBeGreaterThan(barcodeBefore - 1);

      // Toggle hide/show mid-session — store sync reflows without reload.
      await owner.page.getByTestId('column-visibility-toggle').click();
      await owner.page.getByTestId('column-visibility-barcode').click();
      await expect(owner.page.locator('th[data-column-id="barcode"]')).toHaveCount(0);
      await owner.page.getByTestId('column-visibility-barcode').click();
      await expect(owner.page.locator('th[data-column-id="barcode"]')).toBeVisible();
      await owner.page.keyboard.press('Escape');

      const stored = await owner.page.evaluate((key) => localStorage.getItem(key), GRID_LS_KEY);
      expect(stored).toBeTruthy();
    } finally {
      await owner.close();
    }
  });

  test('tablet: sheds compliance columns; keeps ops pillars; touch row height', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await openProducts(owner.page, 900, 700);
      const shell = owner.page.getByTestId('products-grid-shell');
      await expect(shell).toHaveAttribute('data-layout', 'tablet');
      await expect(owner.page.getByTestId('virtualized-table')).toBeVisible({ timeout: 20_000 });

      // Enable compliance cols in the store/menu — tablet layout must still shed them.
      await showAllHideableColumns(owner.page);

      const grid = owner.page.getByTestId('virtualized-table-grid');
      const visible = (await grid.getAttribute('data-visible-columns')) ?? '';
      expect(visible).toMatch(/sku/);
      expect(visible).toMatch(/name/);
      expect(visible).toMatch(/onHand/);
      expect(visible).not.toMatch(/hsTariffCode/);
      expect(visible).not.toMatch(/abcClassification/);
      expect(visible).not.toMatch(/storageTempZone/);
      expect(visible).not.toMatch(/isFragile/);
      expect(visible).not.toMatch(/countryOfOrigin/);

      await expect(owner.page.locator('th[data-column-id="onHand"]')).toBeVisible();
      await expect(owner.page.locator('th[data-column-id="allocated"]')).toBeVisible();
      await expect(owner.page.locator('th[data-column-id="atp"]')).toBeVisible();
      await expect(owner.page.locator('th[data-column-id="hsTariffCode"]')).toHaveCount(0);

      const rowPx = await owner.page.getByTestId('virtualized-table').getAttribute('data-row-px');
      expect(Number(rowPx)).toBeGreaterThanOrEqual(48);
    } finally {
      await owner.close();
    }
  });

  test('mobile: table unmounted; card stack with SKU, location chip, stock split', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await openProducts(owner.page, 390, 844);
      const shell = owner.page.getByTestId('products-grid-shell');
      await expect(shell).toHaveAttribute('data-layout', 'mobile');

      await expect(owner.page.getByTestId('virtualized-table')).toHaveCount(0);
      await expect(owner.page.getByTestId('products-mobile-list')).toBeVisible({ timeout: 20_000 });

      const card = owner.page.getByTestId('products-mobile-card').first();
      await expect(card).toBeVisible();
      await expect(card.getByTestId('products-mobile-location')).toBeVisible();
      await expect(card).toContainText(/OH/i);
      await expect(card).toContainText(/Alloc/i);
      await expect(card).toContainText(/ATP/i);

      await card.click();
      // Peek drawer / selection should respond on touch cards.
      await expect
        .poll(async () => card.getAttribute('aria-selected'))
        .toBe('true');
    } finally {
      await owner.close();
    }
  });

  test('user playthrough: density + columns chaos then restore desktop scroll', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await openProducts(owner.page, 1366, 768);

      // Density flips should keep sticky headers alive.
      const density = owner.page.getByTestId('density-toggle');
      if (await density.isVisible().catch(() => false)) {
        await density.click();
        const compact = owner.page.getByTestId('density-option-compact');
        if (await compact.isVisible().catch(() => false)) {
          await compact.click();
        }
      }

      await showAllHideableColumns(owner.page);
      const scrollport = owner.page.getByTestId('virtualized-table-scrollport');
      await scrollport.evaluate((el) => {
        el.scrollLeft = 180;
        el.scrollTop = 120;
      });
      expect(await scrollport.evaluate((el) => el.scrollLeft)).toBeGreaterThan(20);

      // Hide half the advanced columns — width should shrink / scroll ease.
      await owner.page.getByTestId('column-visibility-toggle').click();
      for (const id of [
        'weight',
        'dims',
        'hsTariffCode',
        'countryOfOrigin',
        'lifecycleStatus',
      ]) {
        const t = owner.page.getByTestId(`column-visibility-${id}`);
        if ((await t.count()) === 0) continue;
        await t.scrollIntoViewIfNeeded();
        if ((await t.getAttribute('data-state')) === 'checked') {
          await t.click();
        }
      }
      await owner.page.keyboard.press('Escape');

      await expect(owner.page.locator('th[data-column-id="sku"]')).toBeVisible();
      await expect(owner.page.locator('th[data-column-id="onHand"]')).toBeVisible();
      await expect(owner.page.locator('th[data-column-id="hsTariffCode"]')).toHaveCount(0);

      // Resize down to mobile mid-session then back up.
      await owner.page.setViewportSize({ width: 375, height: 720 });
      await expect(owner.page.getByTestId('products-grid-shell')).toHaveAttribute(
        'data-layout',
        'mobile',
      );
      await expect(owner.page.getByTestId('products-mobile-list')).toBeVisible();

      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await expect(owner.page.getByTestId('products-grid-shell')).toHaveAttribute(
        'data-layout',
        'desktop',
      );
      await expect(owner.page.getByTestId('virtualized-table')).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
