import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 39 — Products grid fills viewport; dashboard main scroll uses fade cues.
 */
test.describe('Journey 39: Products table width & dashboard scroll fade', () => {
  test.setTimeout(120_000);

  test('products table fills width; dashboard scroll bar hidden with fold cue', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1440, height: 800 });

      await owner.page.goto('/products');
      await expect(owner.page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
        timeout: 45_000,
      });
      const scrollport = owner.page.getByTestId('virtualized-table-scrollport');
      const grid = owner.page.getByTestId('virtualized-table-grid');
      await expect(grid).toBeVisible({ timeout: 20_000 });
      await expect
        .poll(async () => grid.getAttribute('data-visible-columns'))
        .toMatch(/^thumb,sku,name/);

      await expect
        .poll(async () =>
          scrollport.evaluate((port) => {
            const table = port.querySelector('[data-testid="virtualized-table-grid"]');
            if (!(table instanceof HTMLElement)) return 999;
            return Math.abs(table.getBoundingClientRect().width - port.clientWidth);
          }),
        )
        .toBeLessThan(8);

      // Name and barcode headers should not overlap.
      const nameBox = await owner.page.locator('th[data-column-id="name"]').boundingBox();
      const barcodeBox = await owner.page.locator('th[data-column-id="barcode"]').boundingBox();
      expect(nameBox && barcodeBox).toBeTruthy();
      expect(barcodeBox!.x).toBeGreaterThanOrEqual(nameBox!.x + nameBox!.width - 1);

      await owner.page.goto('/dashboard');
      await expect(owner.page.getByTestId('floating-kpi-row')).toBeVisible({ timeout: 30_000 });
      const mainScroll = owner.page.getByTestId('app-main-scroll');
      await expect(mainScroll).toBeVisible();
      expect(
        await mainScroll.evaluate(
          (el) =>
            getComputedStyle(el).scrollbarWidth === 'none' ||
            el.classList.contains('scrollbar-none'),
        ),
      ).toBeTruthy();

      if (await mainScroll.evaluate((el) => el.scrollHeight > el.clientHeight + 40)) {
        await expect(owner.page.getByTestId('app-main-scroll-scroll-down')).toBeVisible({
          timeout: 5_000,
        });
        await mainScroll.evaluate((el) => {
          el.scrollTop = 240;
        });
        expect(await mainScroll.evaluate((el) => el.scrollTop)).toBeGreaterThan(40);
        await expect(owner.page.getByTestId('app-main-scroll-scroll-up')).toBeVisible({
          timeout: 5_000,
        });
      }
    } finally {
      await owner.close();
    }
  });
});
