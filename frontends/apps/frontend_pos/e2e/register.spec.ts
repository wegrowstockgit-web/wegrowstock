import { expect, test } from '@playwright/test';

test('POS login posts targetApp POS for the cross-app gate', async ({ page }) => {
  const loginPosted = page.waitForRequest(
    (request) => request.url().includes('/api/v1/auth/login') && request.method() === 'POST',
  );
  await page.goto('/login');
  await page.getByTestId('pos-login-email').fill('owner@demo.test');
  await page.getByTestId('pos-login-password').fill('password123');
  await page.getByRole('button', { name: /open register|abrir caja|ouvrir la caisse/i }).click();
  const request = await loginPosted;
  expect(request.postDataJSON().targetApp).toBe('POS');
});

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

test('line void and manager-PIN transaction void write the local audit trail', async ({ page }) => {
  await page.goto('/');
  const search = page.getByTestId('pos-upc-search');
  await search.waitFor();
  await search.fill('7501234567890');
  await page.getByTestId('pos-upc-add').click();
  await expect(page.getByTestId('cart-row-7501234567890')).toBeVisible();
  await page.getByTestId('cart-remove-7501234567890').click();
  await expect(page.getByTestId('cart-row-7501234567890')).toHaveCount(0);

  await search.fill('049000042566');
  await page.getByTestId('pos-upc-add').click();
  await expect(page.getByTestId('cart-row-049000042566')).toBeVisible();
  await page.getByTestId('void-transaction').click();
  await expect(page.getByTestId('void-confirm-modal')).toBeVisible();
  await expect(page.getByTestId('void-confirm-yes')).toBeDisabled();
  await page.getByTestId('scanner-pin-digit-1').click();
  await page.getByTestId('scanner-pin-digit-2').click();
  await page.getByTestId('scanner-pin-digit-3').click();
  await page.getByTestId('scanner-pin-digit-4').click();
  await expect(page.getByTestId('void-confirm-yes')).toBeEnabled();
  await page.getByTestId('void-confirm-yes').click();
  await expect(page.getByTestId('cart-row-049000042566')).toHaveCount(0);

  const audit = await page.evaluate(async () => {
    const request = indexedDB.open('invsys-pos');
    return new Promise<Array<{ eventType: string; valueVoided: number }>>((resolve, reject) => {
      request.onerror = () => reject(request.error);
      request.onsuccess = () => {
        const db = request.result;
        const tx = db.transaction('audit_events', 'readonly');
        const store = tx.objectStore('audit_events');
        const req = store.getAll();
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      };
    });
  });
  expect(audit.some((row) => row.eventType === 'LINE_VOID' && row.valueVoided === 12.5)).toBe(true);
  expect(audit.some((row) => row.eventType === 'TX_VOID')).toBe(true);
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
