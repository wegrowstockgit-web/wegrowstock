import { expect, test, type Locator, type Page } from '@playwright/test';
import { completeScannerPin, loginAsDemo } from './fixtures/roleFixture';

async function padScrollport(scrollport: Locator) {
  await scrollport.evaluate((el) => {
    if (el.scrollHeight > el.clientHeight + 120) return;
    const pad = document.createElement('div');
    pad.style.height = '1400px';
    pad.dataset.e2ePad = 'true';
    el.appendChild(pad);
  });
}

async function expectStickyHeader(header: Locator, scrollport: Locator) {
  await expect(header).toBeVisible();
  await expect(header).toHaveClass(/sticky/);
  expect(await header.evaluate((el) => getComputedStyle(el).position)).toBe('sticky');

  await padScrollport(scrollport);
  await scrollport.evaluate((el) => {
    el.scrollTop = 420;
  });
  await expect.poll(async () => scrollport.evaluate((el) => el.scrollTop)).toBeGreaterThan(300);
  await expect(header).toBeVisible();
  const headerBox = await header.boundingBox();
  expect(headerBox).toBeTruthy();
  expect(headerBox!.height).toBeGreaterThan(0);
}

test.describe('Sticky table headers', () => {
  test('list pages have one scrollport and sticky headers', async ({ page }) => {
    await loginAsDemo(page);
    await page.goto('/purchase-orders');
    await completeScannerPin(page);
    await expect(page.getByRole('heading', { name: 'Purchase Orders', exact: true })).toBeVisible({
      timeout: 20_000,
    });

    const numberHeader = page.getByRole('columnheader', { name: /number/i }).first();
    await expect(numberHeader).toBeVisible({ timeout: 20_000 });

    await expect(page.locator('[data-table-scrollport]')).toHaveCount(0);
    const listScroll = page.locator('[data-list-scrollport="true"]');
    await expect(listScroll).toHaveCount(1);

    await expectStickyHeader(numberHeader, listScroll);
  });

  test('virtualized Products grid headers stay visible while rows scroll', async ({ page }) => {
    await loginAsDemo(page);
    await page.goto('/products');
    await completeScannerPin(page);
    await expect(page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
      timeout: 20_000,
    });

    const grid = page.getByTestId('virtualized-table');
    await expect(grid).toBeVisible({ timeout: 20_000 });
    const skuHeader = page.getByRole('columnheader', { name: /sku/i }).first();
    const scrollport = page.getByTestId('virtualized-table-scrollport');

    await padScrollport(scrollport);
    const headerYBefore = (await skuHeader.boundingBox())!.y;
    await scrollport.evaluate((el) => {
      el.scrollTop = 280;
    });
    await expect.poll(async () => scrollport.evaluate((el) => el.scrollTop)).toBeGreaterThan(100);
    const headerYAfter = (await skuHeader.boundingBox())!.y;
    expect(Math.abs(headerYAfter - headerYBefore)).toBeLessThan(4);
  });
});
