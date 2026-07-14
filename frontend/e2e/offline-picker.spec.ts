import { expect, hidScan, test } from './fixtures/roleFixture';

const DEMO_BARCODE = '8901000000001';

test.describe('Offline picker mutation queue', () => {
  test('queues HID scan offline, shows undo toast, then flushes on reconnect', async ({
    pickerPage,
  }) => {
    const pickerContext = pickerPage.context();

    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    await pickerPage.getByRole('button', { name: 'Single' }).click();
    await pickerPage.getByRole('radio', { name: 'Pick' }).click();

    await pickerContext.setOffline(true);

    const outboundScans: string[] = [];
    pickerPage.on('request', (req) => {
      if (req.url().includes('/api/v1/fulfillment/scan') && req.method() === 'POST') {
        outboundScans.push(req.url());
      }
    });

    await hidScan(pickerPage, DEMO_BARCODE);

    await expect(pickerPage.getByText(/Scan queued — undo within 5s/i)).toBeVisible();
    await expect(pickerPage.getByText(DEMO_BARCODE).first()).toBeVisible();
    expect(outboundScans).toHaveLength(0);

    // Wait for the 5s undo buffer to commit into IndexedDB before reconnecting.
    await expect(pickerPage.getByText(/Scan queued — undo within 5s/i)).toBeHidden({
      timeout: 8_000,
    });

    const flushPromise = pickerPage.waitForRequest(
      (req) => req.url().includes('/api/v1/fulfillment/scan') && req.method() === 'POST',
      { timeout: 20_000 }
    );

    await pickerContext.setOffline(false);
    await pickerPage.evaluate(() => window.dispatchEvent(new Event('online')));

    const flushed = await flushPromise;
    expect(flushed.method()).toBe('POST');

    await expect
      .poll(async () => {
        return pickerPage.evaluate(async () => {
          return new Promise<number>((resolve) => {
            const open = indexedDB.open('keyval-store');
            open.onerror = () => resolve(-1);
            open.onsuccess = () => {
              const db = open.result;
              if (!db.objectStoreNames.contains('keyval')) {
                resolve(0);
                return;
              }
              const tx = db.transaction('keyval', 'readonly');
              const store = tx.objectStore('keyval');
              const req = store.get('invsys-mutation-queue');
              req.onsuccess = () => {
                const value = req.result as unknown[] | undefined;
                resolve(Array.isArray(value) ? value.length : 0);
              };
              req.onerror = () => resolve(-1);
            };
          });
        });
      })
      .toBe(0);
  });
});
