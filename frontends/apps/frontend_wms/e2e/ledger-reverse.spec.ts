import { expect, test } from './fixtures/roleFixture';

test.describe('Ledger reverse transaction (Surface A)', () => {
  test('dashboard ledger history can reverse a receive via confirm dialog', async ({
    ownerPage: page,
  }) => {
    const variantsRes = await page.request.get('/api/v1/variants?limit=1');
    expect(variantsRes.ok()).toBeTruthy();
    const variantsBody = await variantsRes.json();
    const variantId = variantsBody.items?.[0]?.id ?? variantsBody[0]?.id;
    expect(variantId).toBeTruthy();

    const locationsRes = await page.request.get('/api/v1/locations');
    expect(locationsRes.ok()).toBeTruthy();
    const locations = await locationsRes.json();
    const locationList = Array.isArray(locations) ? locations : (locations.items ?? []);
    const location =
      locationList.find((l: { type?: string }) => l.type === 'BIN' || l.type === 'WAREHOUSE') ??
      locationList[0];
    expect(location?.id).toBeTruthy();

    const receiveRes = await page.request.post('/api/v1/inventory/receive', {
      data: {
        variantId,
        locationId: location.id,
        quantity: 1,
        referenceType: 'E2E_REVERSE',
      },
    });
    expect(receiveRes.ok()).toBeTruthy();
    const received = await receiveRes.json();
    expect(received.id).toBeTruthy();

    const ledgerRes = await page.request.get('/api/v1/inventory/ledger?limit=50');
    expect(ledgerRes.ok()).toBeTruthy();
    const ledger = await ledgerRes.json();
    expect(ledger.some((row: { id: string }) => row.id === received.id)).toBeTruthy();

    await page.goto('/reports?tab=audit');
    await expect(page.getByTestId('reports-audit-panel')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('ledger-history-table')).toBeVisible({ timeout: 20_000 });

    const undo = page.getByTestId(`reverse-ledger-${received.id}`);
    await expect(undo).toBeVisible({ timeout: 20_000 });
    await undo.click();

    await expect(page.getByRole('heading', { name: 'Reverse Transaction?' })).toBeVisible();
    await expect(
      page.getByText(/This will instantly correct your inventory balances/i),
    ).toBeVisible();
    await page.getByTestId('alert-dialog-confirm').click();

    await expect(page.getByText(/Transaction reversed/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('ERROR_CORRECTION').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId(`reverse-ledger-${received.id}`)).toHaveCount(0);
  });
});
