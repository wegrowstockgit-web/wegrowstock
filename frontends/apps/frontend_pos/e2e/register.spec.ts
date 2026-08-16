import { expect, test } from '@playwright/test';

test('tender writes a local outbox receipt and flashes next-customer', async ({ page }) => {
  await page.goto('/');
  const search = page.getByTestId('pos-upc-search');
  await search.waitFor();
  await search.fill('7501234567890');
  await page.getByTestId('pos-upc-add').click();
  await expect(page.getByTestId('cart-row-7501234567890')).toBeVisible();
  await page.getByTestId('tender-exact').click();
  await expect(page.getByTestId('pos-success-overlay')).toBeVisible();

  const outboxCount = await page.evaluate(async () => {
    const request = indexedDB.open('invsys-pos');
    return new Promise<number>((resolve, reject) => {
      request.onerror = () => reject(request.error);
      request.onsuccess = () => {
        const db = request.result;
        const tx = db.transaction('outbox_receipts', 'readonly');
        const count = tx.objectStore('outbox_receipts').count();
        count.onsuccess = () => resolve(count.result);
        count.onerror = () => reject(count.error);
      };
    });
  });
  expect(outboxCount).toBe(1);
});

test('login and register stay usable on a phone viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/login');
  await expect(page.getByTestId('pos-login')).toBeVisible();
  await expect(page.getByTestId('pos-login-email')).toBeVisible();
  await page.goto('/');
  await expect(page.getByTestId('register-page')).toBeVisible();
  await expect(page.getByTestId('pos-upc-search')).toBeVisible();
  await expect(page.getByTestId('tender-exact')).toBeVisible();
});
