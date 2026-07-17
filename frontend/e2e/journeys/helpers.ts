import type { Browser, BrowserContext, Page } from '@playwright/test';
import { expect, hidScan } from '../fixtures/roleFixture';

export const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
export const WIDGET_S_BARCODE = '8901000000001';
export const WIDGET_S_SKU = 'WIDGET-S';
/** High-velocity sellable SKU (WIDGET-L is a kit in seed — use WIDGET-S for non-kit allocate). */
export const CROSS_DOCK_SKU = WIDGET_S_SKU;
export const CROSS_DOCK_BARCODE = WIDGET_S_BARCODE;
export const GADGET_BLK_SKU = 'GADGET-BLK';
export const GADGET_BLK_BARCODE = '8901000000003';
export const BOLT_BARCODE = '8901000000005';
export const BOX_MED_BARCODE = '8901000000007';
export const WH_01 = 'a0000000-0000-4000-8000-000000000601';
/** Shipping staging lane used by cross-dock routing. */
export const STAGING_S01 = 'a0000000-0000-4000-8000-000000000631';
export const STAGING_PATH = 'WH-01/Z-SHIP/S-01';
export const STAGING_BARCODE = 'S-01';
export const RESERVE_BIN_PATH = 'Z-A/A-1/B-01';
export const SHIPPED_SO_ID = 'a0000000-0000-4000-8000-000000001501';
export const SHIPPED_SO_LINE_ID = 'a0000000-0000-4000-8000-000000001601';
/** Client A — Metro Distributors (seed B2B portal user). */
export const CLIENT_A_METRO_ID = 'a0000000-0000-4000-8000-000000001102';
/** Client B — Retail Partners LLC (cubic volume SLA). */
export const CLIENT_B_RETAIL_ID = 'a0000000-0000-4000-8000-000000001101';
export const DEMO_PICKER_USER_ID = 'a0000000-0000-4000-8000-000000000204';
export const PICK_BIN_ID = 'a0000000-0000-4000-8000-000000000604';
/** Secondary pick bin — quieter than shipping staging for cycle-count E2E. */
export const PICK_BIN_B02_ID = 'a0000000-0000-4000-8000-000000000605';

type JourneyRole = 'owner' | 'admin' | 'manager' | 'picker' | 'b2b';

const ROLE_EMAIL: Record<JourneyRole, string> = {
  owner: 'owner@demo.test',
  admin: 'admin@demo.test',
  manager: 'manager@demo.test',
  picker: 'picker@demo.test',
  b2b: 'b2b@demo.test',
};

/**
 * Isolated browser context per role with a fresh API login.
 * Avoids stale refresh cookies from globalSetup storageState (single-use rotation).
 */
export async function contextForRole(
  browser: Browser,
  role: JourneyRole,
): Promise<{ context: BrowserContext; page: Page; close: () => Promise<void> }> {
  const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();

  let loginRes = await page.request.post('/api/v1/auth/login', {
    data: { email: ROLE_EMAIL[role], password: DEMO_PASSWORD },
  });
  for (let attempt = 0; !loginRes.ok() && loginRes.status() === 429 && attempt < 4; attempt += 1) {
    await page.waitForTimeout(15_000 * (attempt + 1));
    loginRes = await page.request.post('/api/v1/auth/login', {
      data: { email: ROLE_EMAIL[role], password: DEMO_PASSWORD },
    });
  }
  if (!loginRes.ok()) {
    const status = loginRes.status();
    const body = await loginRes.text().catch(() => '');
    await context.close();
    throw new Error(`Login failed for ${ROLE_EMAIL[role]}: ${status} ${body}`);
  }
  const session = (await loginRes.json()) as {
    userId: string;
    tenantId: string;
    roles: string[];
    warehouseIds?: string[];
  };
  const meRes = await page.request.get('/api/v1/auth/me');
  const me = meRes.ok()
    ? ((await meRes.json()) as {
        userId: string;
        tenantId: string;
        email: string;
        displayName: string;
        roles: string[];
        warehouseIds?: string[];
      })
    : null;

  await page.goto('/login');
  await page.evaluate(
    ({ user }) => {
      localStorage.setItem(
        'invsys-session',
        JSON.stringify({
          state: {
            authenticated: true,
            user,
            lastRequestId: null,
            primarySession: null,
          },
          version: 0,
        }),
      );
    },
    {
      user: {
        id: me?.userId ?? session.userId,
        email: me?.email ?? ROLE_EMAIL[role],
        displayName: me?.displayName ?? role,
        roles: me?.roles ?? session.roles ?? [],
        warehouseIds: me?.warehouseIds ?? session.warehouseIds ?? [],
        tenantId: me?.tenantId ?? session.tenantId,
      },
    },
  );

  return {
    context,
    page,
    close: async () => context.close(),
  };
}

export async function freshLogin(
  browser: Browser,
  email: string,
  password = DEMO_PASSWORD,
): Promise<{ context: BrowserContext; page: Page; close: () => Promise<void> }> {
  const context = await browser.newContext({
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
  });
  const page = await context.newPage();
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).not.toHaveURL(/\/login/, { timeout: 25_000 });
  return {
    context,
    page,
    close: async () => context.close(),
  };
}

export async function apiJson<T>(page: Page, url: string, init?: RequestInit): Promise<T> {
  const res = await page.request.fetch(url, {
    method: (init?.method as 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE') ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers as Record<string, string> | undefined),
    },
    data: init?.body ? JSON.parse(init.body as string) : undefined,
  });
  if (!res.ok()) {
    throw new Error(`${init?.method ?? 'GET'} ${url} → ${res.status()} ${await res.text()}`);
  }
  return (await res.json()) as T;
}

export async function findVariantId(page: Page, sku = WIDGET_S_SKU): Promise<string> {
  const pageData = await apiJson<{ items: Array<{ id: string; sku: string }> }>(
    page,
    '/api/v1/variants?limit=200',
  );
  const found = pageData.items.find((v) => v.sku === sku);
  if (!found) throw new Error(`Variant ${sku} not found`);
  return found.id;
}

export async function firstSupplierId(page: Page): Promise<string> {
  const suppliers = await apiJson<Array<{ id: string }>>(page, '/api/v1/suppliers');
  if (!suppliers[0]) throw new Error('No suppliers');
  return suppliers[0].id;
}

export async function firstCustomerId(page: Page): Promise<string> {
  const customers = await apiJson<Array<{ id: string }>>(page, '/api/v1/customers');
  if (!customers[0]) throw new Error('No customers');
  return customers[0].id;
}

/** Ensure a QUARANTINE bin exists under WH-01 (required for RESTOCK receives). */
export async function ensureQuarantineLocation(page: Page): Promise<string> {
  const locations = await apiJson<Array<{ id: string; type: string; code: string }>>(
    page,
    '/api/v1/locations',
  );
  const existing = locations.find((l) => l.type === 'QUARANTINE');
  if (existing) return existing.id;

  const created = await apiJson<{ id: string }>(page, '/api/v1/locations', {
    method: 'POST',
    body: JSON.stringify({
      parentLocationId: WH_01,
      type: 'QUARANTINE',
      code: `Q-J-${Date.now().toString(36)}`,
      name: 'Journey Quarantine',
      path: `WH-01/Q-J`,
    }),
  });
  return created.id;
}

/** Surface B page title — avoid /Fulfillment|Floor/i (matches both nav "Floor ops" and h1). */
export async function expectFulfillmentSurface(page: Page, timeout = 20_000): Promise<void> {
  await expect(page).toHaveURL(/\/fulfillment/, { timeout });
  await expect(page.getByRole('heading', { name: 'Fulfillment', exact: true })).toBeVisible({
    timeout,
  });
}

/** Drain all on-hand for a variant so the next allocate() yields BACKORDERED. */
export async function drainVariantOnHand(page: Page, variantId: string): Promise<void> {
  const levelsRes = await page.request.get(`/api/v1/inventory/levels?variantId=${variantId}`);
  if (!levelsRes.ok()) return;
  const levels = (await levelsRes.json()) as Array<{
    locationId: string;
    onHand?: number;
    lotId?: string | null;
  }>;
  for (const row of levels) {
    const onHand = Number(row.onHand ?? 0);
    if (onHand <= 0) continue;
    const adjustRes = await page.request.post('/api/v1/inventory/adjust', {
      data: {
        variantId,
        locationId: row.locationId,
        lotId: row.lotId ?? null,
        delta: -onHand,
        reasonCode: 'E2E_DRAIN_FOR_BACKORDER',
      },
    });
    if (!adjustRes.ok()) {
      throw new Error(`Drain adjust failed: ${adjustRes.status()} ${await adjustRes.text()}`);
    }
  }
}

/**
 * Fresh zero-OH sellable SKU for backorder / cross-dock journeys.
 * Avoids polluted demo stock on WIDGET-S from prior E2E runs.
 */
export async function createZeroStockSellableVariant(
  page: Page,
  opts?: { sku?: string; barcode?: string },
): Promise<{ productId: string; variantId: string; sku: string; barcode: string }> {
  const stamp = Date.now().toString(36).toUpperCase();
  const sku = opts?.sku ?? `XD-HV-${stamp}`;
  const barcode = opts?.barcode ?? `89${String(Date.now()).slice(-11)}`;

  const product = await apiJson<{ id: string }>(page, '/api/v1/products', {
    method: 'POST',
    body: JSON.stringify({
      skuRoot: sku,
      name: `Cross-Dock Velocity ${stamp}`,
      description: 'E2E high-velocity backorder item',
    }),
  });

  const variant = await apiJson<{ id: string; sku: string; barcode?: string }>(page, '/api/v1/variants', {
    method: 'POST',
    body: JSON.stringify({
      productId: product.id,
      sku,
      barcode,
      price: 12.5,
      currency: 'USD',
    }),
  });

  return {
    productId: product.id,
    variantId: variant.id,
    sku: variant.sku ?? sku,
    barcode: variant.barcode ?? barcode,
  };
}

/**
 * Create a small shipped SO so RMA journeys have returnable qty (seeded SO may be exhausted).
 */
export async function createShippedSalesOrder(
  page: Page,
  opts: {
    variantId: string;
    customerId: string;
    quantity?: number;
    unitPrice?: number;
    numberPrefix?: string;
  },
): Promise<{ salesOrderId: string; salesOrderLineId: string; number: string }> {
  const qty = opts.quantity ?? 2;
  const unitPrice = opts.unitPrice ?? 12.5;
  const binId = PICK_BIN_ID;

  // Top up free stock at the pick bin so allocate + ship can succeed.
  const receiveRes = await page.request.post('/api/v1/inventory/receive', {
    data: {
      variantId: opts.variantId,
      locationId: binId,
      quantity: qty + 5,
      referenceType: 'E2E_RMA_TOPUP',
    },
  });
  if (!receiveRes.ok()) {
    throw new Error(`RMA top-up receive failed: ${receiveRes.status()} ${await receiveRes.text()}`);
  }

  const so = await apiJson<{ id: string; number: string }>(page, '/api/v1/sales-orders', {
    method: 'POST',
    body: JSON.stringify({
      customerId: opts.customerId,
      channel: 'B2B',
      number: `${opts.numberPrefix ?? 'SO-RMA'}-${Date.now()}`,
      lines: [{ variantId: opts.variantId, qtyOrdered: qty, unitPrice }],
    }),
  });
  await page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
  await page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);

  const detail = await apiJson<{
    id: string;
    lines: Array<{ id: string; qtyOrdered: number }>;
  }>(page, `/api/v1/sales-orders/${so.id}`);
  const line = detail.lines[0];
  if (!line) throw new Error('SO missing lines');

  const shipRes = await page.request.post('/api/v1/shipments', {
    headers: {
      'Content-Type': 'application/json',
      'X-Warehouse-Id': WH_01,
    },
    data: {
      salesOrderId: so.id,
      carrier: 'GROUND',
      trackingNumber: `RMA-${Date.now()}`,
      lines: [{ salesOrderLineId: line.id, quantity: qty }],
    },
  });
  if (!shipRes.ok()) {
    throw new Error(`RMA seed ship failed: ${shipRes.status()} ${await shipRes.text()}`);
  }

  return { salesOrderId: so.id, salesOrderLineId: line.id, number: so.number };
}

/** Invite + accept a B2B_CUSTOMER portal user linked to a customer. */
export async function inviteAndAcceptB2b(
  browser: Browser,
  adminPage: Page,
  opts: { email: string; customerId: string; displayName?: string },
): Promise<{ context: BrowserContext; page: Page; close: () => Promise<void> }> {
  const invite = await apiJson<{ token: string }>(adminPage, '/api/v1/users/invitations', {
    method: 'POST',
    body: JSON.stringify({
      email: opts.email,
      role: 'B2B_CUSTOMER',
      customerId: opts.customerId,
    }),
  });

  const acceptCtx = await browser.newContext({
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
  });
  const acceptPage = await acceptCtx.newPage();
  try {
    await acceptPage.goto(`/invite/${invite.token}`);
    await expect(acceptPage.getByTestId('invite-accept-page')).toBeVisible({ timeout: 15_000 });
    await acceptPage.getByLabel('First Name').fill(opts.displayName ?? 'Client');
    await acceptPage.getByLabel('Last Name').fill('B');
    await acceptPage.getByLabel('Password', { exact: true }).fill(DEMO_PASSWORD);
    await acceptPage.getByLabel('Confirm Password').fill(DEMO_PASSWORD);
    const acceptWait = acceptPage.waitForResponse(
      (res) => res.url().includes('/api/v1/invitations/accept') && res.request().method() === 'POST',
    );
    await acceptPage.getByRole('button', { name: 'Join team' }).click();
    const acceptRes = await acceptWait;
    if (!acceptRes.ok()) {
      throw new Error(`Invite accept failed: ${acceptRes.status()} ${await acceptRes.text()}`);
    }
  } finally {
    await acceptCtx.close();
  }

  return freshLogin(browser, opts.email, DEMO_PASSWORD);
}

export { expect, hidScan };
