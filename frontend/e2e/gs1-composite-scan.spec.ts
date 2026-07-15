import { expect, test } from './fixtures/roleFixture';

/** GS1-128 composite: AI 01 GTIN + AI 10 lot + AI 17 expiry + AI 30 qty */
const GS1_COMPOSITE = '(01)01234567890128(10)BATCH-E2E(17)251231(30)4';

/** Parentheses are unreliable via KeyboardEvent.key — use the intent/custom-event path. */
async function intentScan(page: import('@playwright/test').Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    window.dispatchEvent(new CustomEvent('hardwareScan', { detail: { barcode: code } }));
  }, barcode);
}

test.describe('GS1-128 composite fulfillment scan', () => {
  test('intent scan auto-fills Lot/Expiry/Qty and queues structured offline payload', async ({
    pickerPage,
  }) => {
    const context = pickerPage.context();

    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    await pickerPage.getByRole('button', { name: 'Single' }).click();
    await pickerPage.getByRole('radio', { name: 'Receive' }).click();

    await context.setOffline(true);

    await intentScan(pickerPage, GS1_COMPOSITE);

    // Client-side parse success: green composite card + pre-filled fields (no Wi-Fi needed).
    await expect(pickerPage.getByTestId('gs1-fields-card')).toBeVisible();
    await expect(pickerPage.getByTestId('gs1-lot')).toHaveValue('BATCH-E2E');
    await expect(pickerPage.getByTestId('gs1-expiry')).toHaveValue('2025-12-31');
    await expect(pickerPage.getByTestId('gs1-qty')).toHaveValue('4');
    // Buffer shows GTIN lookup key, not the raw AI blob.
    await expect(pickerPage.getByTestId('scan-buffer-card')).toContainText('01234567890128');

    await expect(pickerPage.getByText(/Scan queued — undo within 5s/i)).toBeVisible();
    await expect(pickerPage.getByText(/Scan queued — undo within 5s/i)).toBeHidden({
      timeout: 8_000,
    });

    const queuedBody = await pickerPage.evaluate(async () => {
      return new Promise<Record<string, unknown> | null>((resolve) => {
        const open = indexedDB.open('keyval-store');
        open.onerror = () => resolve(null);
        open.onsuccess = () => {
          const db = open.result;
          if (!db.objectStoreNames.contains('keyval')) {
            resolve(null);
            return;
          }
          const tx = db.transaction('keyval', 'readonly');
          const store = tx.objectStore('keyval');
          const req = store.get('invsys-mutation-queue');
          req.onsuccess = () => {
            const queue = req.result as Array<{ body?: Record<string, unknown> }> | undefined;
            resolve(Array.isArray(queue) && queue[0]?.body ? queue[0].body : null);
          };
          req.onerror = () => resolve(null);
        };
      });
    });

    expect(queuedBody).toBeTruthy();
    expect(queuedBody?.barcode).toBe('01234567890128');
    expect(queuedBody?.isGs1).toBe(true);
    expect(queuedBody?.gtin).toBe('01234567890128');
    expect(queuedBody?.lotNumber).toBe('BATCH-E2E');
    expect(queuedBody?.expiryDate).toBe('2025-12-31');
    expect(queuedBody?.quantity).toBe(4);
    expect(String(queuedBody?.rawBarcode ?? '')).toContain('(01)');
  });
});
