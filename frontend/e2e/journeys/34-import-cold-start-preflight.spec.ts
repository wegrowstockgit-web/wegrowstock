import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, apiJson, WH_01 } from './helpers';

/**
 * Journey 34 — Enterprise import cold-start:
 * upload → preflight (missing product) → create missing → re-preflight READY →
 * import ready rows → correlate variant + ledger DATA_IMPORT + audit checksum.
 */
test.describe('Journey 34: Import cold-start preflight', () => {
  test.setTimeout(240_000);

  test('UI preflight + create-missing + import correlates ledger and audit', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    const page = owner.page;
    const sku = `E2E-IMP-${Date.now().toString(36).toUpperCase()}`;
    const qty = '7';
    // Use fallback warehouse (no location_path) so mapping stays unambiguous in the wizard.
    const csv = [
      'sku,name,qty,unitCost,length,width,height,hsCode,palletTie,palletHigh,tempZone,lifecycleStatus',
      `${sku},Cold Start Widget,${qty},3.50,10,8,6,8471.30,10,4,AMBIENT,ACTIVE`,
    ].join('\n');

    try {
      await page.goto('/import');
      await expect(page.getByTestId('import-wizard')).toBeVisible({ timeout: 20_000 });
      await page.getByRole('button', { name: 'CSV import' }).click();
      await expect(page.getByRole('heading', { name: 'Data import' })).toBeVisible();

      const preflightPromise = page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/ingestion/preflight') && r.request().method() === 'POST',
        { timeout: 45_000 },
      );

      await page.locator('#ingestion-file-input').setInputFiles({
        name: `${sku}.csv`,
        mimeType: 'text/csv',
        buffer: Buffer.from(csv, 'utf-8'),
      });

      await expect(page.getByText(`${sku}.csv`)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('validation-preview')).toBeVisible();

      const preflightRes = await preflightPromise;
      expect(preflightRes.ok(), await preflightRes.text()).toBeTruthy();
      const pf = (await preflightRes.json()) as {
        rows: Array<{ sku: string; status: string }>;
        fileChecksumSha256: string;
      };
      const coldRow = pf.rows.find((r) => r.sku === sku);
      expect(coldRow?.status, JSON.stringify(pf.rows)).toBe('MISSING_PRODUCT');

      await expect(page.getByText('MISSING_PRODUCT').first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('bulk-actions')).toBeVisible();
      await expect(page.getByTestId('preflight-row-2')).toHaveAttribute(
        'data-status',
        'MISSING_PRODUCT',
      );

      const beforeVariants = await apiJson<{ items: Array<{ sku: string }> }>(
        page,
        '/api/v1/variants?limit=500',
      );
      expect(beforeVariants.items.some((v) => v.sku === sku)).toBeFalsy();

      const createRespPromise = page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/ingestion/create-missing-products') &&
          r.request().method() === 'POST',
        { timeout: 45_000 },
      );
      const reflightPromise = page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/ingestion/preflight') && r.request().method() === 'POST',
        { timeout: 45_000 },
      );
      await page.getByTestId('create-missing-products').click();
      const createResp = await createRespPromise;
      expect(createResp.ok(), await createResp.text()).toBeTruthy();
      const createBody = (await createResp.json()) as { created: number };
      expect(createBody.created).toBeGreaterThanOrEqual(1);

      const reflight = await reflightPromise;
      expect(reflight.ok(), await reflight.text()).toBeTruthy();
      await expect(page.getByText('READY_TO_IMPORT').first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByTestId('preflight-row-2')).toHaveAttribute(
        'data-status',
        'READY_TO_IMPORT',
      );

      const afterCreate = await apiJson<{ items: Array<{ id: string; sku: string }> }>(
        page,
        '/api/v1/variants?limit=500',
      );
      const createdVariant = afterCreate.items.find((v) => v.sku === sku);
      expect(createdVariant, `variant ${sku} should exist after create-missing`).toBeTruthy();

      const importRespPromise = page.waitForResponse(
        (r) =>
          r.url().includes('/api/v1/ingestion/import') &&
          r.request().method() === 'POST' &&
          !r.url().includes('legacy'),
        { timeout: 60_000 },
      );
      await page.getByTestId('import-submit').click();
      const importResp = await importRespPromise;
      expect(importResp.ok(), await importResp.text()).toBeTruthy();
      const importBody = (await importResp.json()) as {
        imported: number;
        skipped: number;
        fileChecksumSha256?: string;
      };
      expect(importBody.imported).toBeGreaterThanOrEqual(1);
      expect(importBody.fileChecksumSha256).toBeTruthy();
      expect(importBody.fileChecksumSha256!.length).toBeGreaterThanOrEqual(32);

      await expect(page.getByText(/Imported \d+/i)).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Audit checksum:/i)).toBeVisible();

      const ledger = await apiJson<
        Array<{
          variantId: string;
          reasonCode?: string;
          referenceType?: string;
          quantityDelta?: number | string;
        }>
      >(page, `/api/v1/inventory/ledger?variantId=${createdVariant!.id}&limit=100`);
      const importRows = ledger.filter((row) => {
        const reason = `${row.reasonCode ?? ''} ${row.referenceType ?? ''}`;
        return row.variantId === createdVariant!.id && reason.includes('DATA_IMPORT');
      });
      expect(importRows.length, 'expected DATA_IMPORT ledger row').toBeGreaterThanOrEqual(1);
      const receivedQty = importRows.reduce(
        (sum, row) => sum + Number(row.quantityDelta ?? 0),
        0,
      );
      expect(receivedQty).toBeGreaterThanOrEqual(Number(qty));

      const audit = await apiJson<
        Array<{ action: string; entityType: string; diff?: Record<string, unknown> }>
      >(page, '/api/v1/operations/audit');
      const importAudit = audit.find(
        (a) =>
          a.action === 'DATA_IMPORT' &&
          String(a.diff?.checksumSha256 ?? '') === importBody.fileChecksumSha256,
      );
      expect(importAudit, 'DATA_IMPORT audit entry with matching checksum missing').toBeTruthy();
      expect(importAudit!.entityType).toBe('INGESTION');

      await expect(page.getByTestId('download-import-template')).toBeVisible();
    } finally {
      await owner.close();
    }
  });

  test('API preflight blocks ledger write for missing refs; dims validation correlates', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    const page = owner.page;
    const skuOk = `E2E-PF-OK-${Date.now().toString(36).toUpperCase()}`;
    const skuBad = `E2E-PF-BAD-${Date.now().toString(36).toUpperCase()}`;
    const csv = [
      'sku,name,qty,length,width,height',
      `${skuOk},Has Dims,1,10,8,6`,
      `${skuBad},No Dims,1,,,`,
    ].join('\n');
    const mapping = JSON.stringify({
      sku: 'sku',
      name: 'name',
      qty: 'qty',
      length: 'length',
      width: 'width',
      height: 'height',
    });

    try {
      const preflightRes = await page.request.post('/api/v1/ingestion/preflight', {
        multipart: {
          file: {
            name: 'preflight.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csv, 'utf-8'),
          },
          columnsMapping: mapping,
          locationId: WH_01,
        },
      });
      expect(preflightRes.ok(), await preflightRes.text()).toBeTruthy();
      const pf = (await preflightRes.json()) as {
        rows: Array<{ sku: string; status: string; detail?: string }>;
        fileChecksumSha256: string;
        missingSkus: string[];
      };
      expect(pf.fileChecksumSha256).toBeTruthy();
      const okRow = pf.rows.find((r) => r.sku === skuOk);
      const badRow = pf.rows.find((r) => r.sku === skuBad);
      expect(okRow?.status).toBe('MISSING_PRODUCT');
      expect(badRow?.status).toBe('VALIDATION_ERROR');
      expect(String(badRow?.detail ?? '')).toMatch(/length|dimension/i);
      expect(pf.missingSkus).toContain(skuOk);

      const importRes = await page.request.post('/api/v1/ingestion/import', {
        multipart: {
          file: {
            name: 'preflight.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csv, 'utf-8'),
          },
          columnsMapping: mapping,
          locationId: WH_01,
          createMissingProducts: 'false',
        },
      });
      expect(importRes.ok(), await importRes.text()).toBeTruthy();
      const imported = (await importRes.json()) as { imported: number; skipped: number };
      expect(imported.imported).toBe(0);
      expect(imported.skipped).toBeGreaterThanOrEqual(2);

      const variants = await apiJson<{ items: Array<{ sku: string }> }>(
        page,
        '/api/v1/variants?limit=500',
      );
      expect(variants.items.some((v) => v.sku === skuOk || v.sku === skuBad)).toBeFalsy();
    } finally {
      await owner.close();
    }
  });

  test('location_path resolve-or-create correlates with Map to existing', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    const page = owner.page;
    const sku = `E2E-PATH-${Date.now().toString(36).toUpperCase()}`;
    const path = `WH-01/E2E-ZONE/${sku}`;
    const csv = [
      'sku,name,qty,length,width,height,location_path',
      `${sku},Path Widget,2,11,9,7,${path}`,
    ].join('\n');
    const mapping = JSON.stringify({
      sku: 'sku',
      name: 'name',
      qty: 'qty',
      length: 'length',
      width: 'width',
      height: 'height',
      locationPath: 'location_path',
    });

    try {
      // Catalog first (create-missing), then import with createMissingLocations.
      const createRes = await page.request.post('/api/v1/ingestion/create-missing-products', {
        multipart: {
          file: {
            name: 'path.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csv, 'utf-8'),
          },
          columnsMapping: mapping,
        },
      });
      expect(createRes.ok(), await createRes.text()).toBeTruthy();

      const importRes = await page.request.post('/api/v1/ingestion/import', {
        multipart: {
          file: {
            name: 'path.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from(csv, 'utf-8'),
          },
          columnsMapping: mapping,
          createMissingProducts: 'false',
          createMissingLocations: 'true',
        },
      });
      expect(importRes.ok(), await importRes.text()).toBeTruthy();
      const body = (await importRes.json()) as {
        imported: number;
        fileChecksumSha256?: string;
      };
      expect(body.imported).toBe(1);
      expect(body.fileChecksumSha256).toBeTruthy();

      const locations = await apiJson<Array<{ path: string; code: string }>>(
        page,
        '/api/v1/locations',
      );
      const normalized = (p: string) => p.replace(/^\/+/, '');
      expect(
        locations.some((l) => normalized(l.path) === path),
        `expected location path ${path}`,
      ).toBeTruthy();

      const variants = await apiJson<{ items: Array<{ id: string; sku: string }> }>(
        page,
        '/api/v1/variants?limit=500',
      );
      const variant = variants.items.find((v) => v.sku === sku);
      expect(variant).toBeTruthy();
      const ledger = await apiJson<
        Array<{
          reasonCode?: string;
          referenceType?: string;
          quantityDelta?: number | string;
        }>
      >(page, `/api/v1/inventory/ledger?variantId=${variant!.id}&limit=100`);
      expect(
        ledger.some((r) =>
          `${r.reasonCode ?? ''} ${r.referenceType ?? ''}`.includes('DATA_IMPORT'),
        ),
        JSON.stringify(ledger.slice(0, 5)),
      ).toBeTruthy();
    } finally {
      await owner.close();
    }
  });
});
