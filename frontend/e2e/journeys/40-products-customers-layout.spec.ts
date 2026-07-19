import { completeScannerPin, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 40 — Products grid expands horizontally when columns are enabled;
 * header/body column ids stay aligned; Customers page scrolls via AppShell
 * (no nested overflow trap).
 */
test.describe('Journey 40: Products auto-layout & customers scrollport', () => {
  test.setTimeout(180_000);

  test('products columns expand with horizontal scroll; customers main scroll works', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1100, height: 720 });
      await owner.page.goto('/products');
      await expect(owner.page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
        timeout: 45_000,
      });
      await completeScannerPin(owner.page);

      const scrollport = owner.page.getByTestId('virtualized-table-scrollport');
      const grid = owner.page.getByTestId('virtualized-table-grid');
      await expect(grid).toBeVisible({ timeout: 20_000 });

      expect(
        await scrollport.evaluate((el) => el.classList.contains('scrollbar-thin')),
      ).toBeTruthy();

      // Thumb cell is a fixed square sandbox (no blow-out).
      const thumbCell = owner.page.locator('td[data-column-id="thumb"]').first();
      await expect(thumbCell).toBeVisible();
      const thumbBox = await thumbCell.locator('.h-12.w-12').first().boundingBox();
      expect(thumbBox).toBeTruthy();
      expect(Math.abs((thumbBox?.width ?? 0) - (thumbBox?.height ?? 0))).toBeLessThan(2);

      // Enable several advanced columns — table should grow beyond the viewport.
      await completeScannerPin(owner.page);
      await owner.page.getByTestId('column-visibility-toggle').click();
      const menu = owner.page.getByTestId('column-visibility-menu');
      await expect(menu).toBeVisible();

      for (const id of ['weight', 'dims', 'hsTariffCode', 'lifecycleStatus', 'isHazmat']) {
        const toggle = owner.page.getByTestId(`column-visibility-${id}`);
        await toggle.scrollIntoViewIfNeeded();
        if ((await toggle.getAttribute('data-state')) !== 'checked') {
          await toggle.click();
        }
        await expect(owner.page.locator(`th[data-column-id="${id}"]`)).toBeVisible();
      }
      await owner.page.keyboard.press('Escape');

      await expect
        .poll(async () =>
          scrollport.evaluate((port) => {
            const table = port.querySelector('[data-testid="virtualized-table-grid"]');
            if (!(table instanceof HTMLElement)) return false;
            return table.scrollWidth > port.clientWidth + 24 || port.scrollWidth > port.clientWidth + 8;
          }),
        )
        .toBeTruthy();

      // Header / first body row share identical column id order.
      const aligned = await owner.page.evaluate(() => {
        const table = document.querySelector('[data-testid="virtualized-table-grid"]');
        if (!table) return false;
        const header = Array.from(table.querySelectorAll('thead th[data-column-id]')).map((el) =>
          el.getAttribute('data-column-id'),
        );
        const bodyRow = table.querySelector('tbody tr:not([aria-hidden])');
        if (!bodyRow) return false;
        const body = Array.from(bodyRow.querySelectorAll('td[data-column-id]')).map((el) =>
          el.getAttribute('data-column-id'),
        );
        return header.length > 0 && header.join(',') === body.join(',');
      });
      expect(aligned).toBeTruthy();

      // Customers: page itself must not own a trapped overflow-y scrollport.
      await owner.page.goto('/customers');
      await expect(owner.page.getByTestId('customers-page')).toBeVisible({ timeout: 30_000 });
      // Page content must not trap vertical scroll (AppShell <main> owns it).
      const pageOverflowY = await owner.page
        .getByTestId('customers-page')
        .evaluate((el) => getComputedStyle(el).overflowY);
      expect(pageOverflowY).toBe('visible');

      const main = owner.page.locator('main').first();
      await expect(main).toBeVisible();
      await expect
        .poll(async () =>
          main.evaluate((el) => {
            const style = getComputedStyle(el);
            return style.overflowY === 'auto' || style.overflowY === 'scroll';
          }),
        )
        .toBeTruthy();
    } finally {
      await owner.close();
    }
  });
});
