import type { Browser, Page } from '@playwright/test';
import { expect, test } from './fixtures/roleFixture';

/**
 * Functional coverage for:
 * - Shift PIN two-step enroll + unlock (Surface B only)
 * - Connected Wi‑Fi badge on devices / floor; hidden on office dashboard
 *
 * Uses a fresh context without installAutoUnlockNavigations so reload cannot
 * race the two-step enrollment UI.
 */

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

async function loginPickerFresh(browser: Browser): Promise<{ page: Page; close: () => Promise<void> }> {
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();
  const loginRes = await page.request.post('/api/v1/auth/login', {
    data: { email: 'picker@demo.test', password: DEMO_PASSWORD },
  });
  expect(loginRes.ok()).toBeTruthy();
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
      localStorage.setItem(
        'invsys-preferences',
        JSON.stringify({
          state: {
            densityMode: 'cozy',
            showOnboardingTour: false,
            activeTourId: null,
            currentTourStep: 0,
            isTourAwaitingRoute: false,
            awaitingRoute: null,
          },
          version: 0,
        }),
      );
    },
    {
      user: {
        id: me?.userId ?? session.userId,
        email: me?.email ?? 'picker@demo.test',
        displayName: me?.displayName ?? 'Picker',
        roles: me?.roles ?? session.roles,
        warehouseIds: me?.warehouseIds ?? session.warehouseIds ?? [],
        tenantId: me?.tenantId ?? session.tenantId,
      },
    },
  );
  return { page, close: () => context.close() };
}

async function clearScannerPinState(page: Page): Promise<void> {
  await page.evaluate(async () => {
    try {
      const dbs = await indexedDB.databases?.();
      if (dbs) {
        await Promise.all(
          dbs
            .filter((d) => d.name)
            .map(
              (d) =>
                new Promise<void>((resolve) => {
                  const req = indexedDB.deleteDatabase(d.name!);
                  req.onsuccess = () => resolve();
                  req.onerror = () => resolve();
                  req.onblocked = () => resolve();
                }),
            ),
        );
      }
    } catch {
      /* best-effort */
    }
    try {
      for (const key of Object.keys(localStorage)) {
        if (key.toLowerCase().includes('salt') || key.toLowerCase().includes('pin')) {
          localStorage.removeItem(key);
        }
      }
    } catch {
      /* ignore */
    }
  });
}

async function tapSetupDigits(page: Page, pin: string): Promise<void> {
  for (const digit of pin) {
    await page.getByTestId(`scanner-setup-digit-${digit}`).click();
  }
}

async function tapUnlockDigits(page: Page, pin: string): Promise<void> {
  for (const digit of pin) {
    await page.getByTestId(`scanner-unlock-digit-${digit}`).click();
  }
}

async function loginOwnerFresh(browser: Browser): Promise<{ page: Page; close: () => Promise<void> }> {
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();
  const loginRes = await page.request.post('/api/v1/auth/login', {
    data: { email: 'owner@demo.test', password: DEMO_PASSWORD },
  });
  expect(loginRes.ok()).toBeTruthy();
  const session = (await loginRes.json()) as {
    userId: string;
    tenantId: string;
    roles: string[];
    warehouseIds?: string[];
  };
  await page.goto('/login');
  await page.evaluate(
    ({ user }) => {
      localStorage.setItem(
        'invsys-session',
        JSON.stringify({
          state: { authenticated: true, user, lastRequestId: null, primarySession: null },
          version: 0,
        }),
      );
      localStorage.setItem(
        'invsys-preferences',
        JSON.stringify({
          state: {
            densityMode: 'cozy',
            showOnboardingTour: false,
            activeTourId: null,
            currentTourStep: 0,
            isTourAwaitingRoute: false,
            awaitingRoute: null,
          },
          version: 0,
        }),
      );
    },
    {
      user: {
        id: session.userId,
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: session.roles,
        warehouseIds: session.warehouseIds ?? [],
        tenantId: session.tenantId,
      },
    },
  );
  return { page, close: () => context.close() };
}

test.describe('Device PIN + connectivity badge', () => {
  test('office dashboard hides Connected badge and does not force PIN UI', async ({ browser }) => {
    const { page, close } = await loginOwnerFresh(browser);
    try {
      await page.goto('/dashboard');
      await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 });
      await expect(page.getByTestId('network-status-badge')).toHaveCount(0);
      await expect(page.getByTestId('scanner-pin-setup-overlay')).toHaveCount(0);
      await expect(page.getByTestId('scanner-lock-overlay')).toHaveCount(0);
    } finally {
      await close();
    }
  });

  test('floor: two-step PIN enroll, Connected badge, lock/unlock', async ({ browser }) => {
    const { page, close } = await loginPickerFresh(browser);
    const pin = '1234';
    try {
      await clearScannerPinState(page);
      await page.goto('/fulfillment');
      await page
        .getByText('Loading session...')
        .waitFor({ state: 'detached', timeout: 30_000 })
        .catch(() => undefined);

      const setup = page.getByTestId('scanner-pin-setup-overlay');
      await expect(setup).toBeVisible({ timeout: 25_000 });
      await expect(setup).toHaveAttribute('data-phase', 'create');
      await expect(page.getByTestId('scanner-setup-step')).toHaveText(/Step 1 of 2/i);
      await expect(page.getByText('Set shift PIN')).toBeVisible();

      await tapSetupDigits(page, pin);
      await expect(setup).toHaveAttribute('data-phase', 'confirm', { timeout: 5_000 });
      await expect(page.getByText('Confirm PIN')).toBeVisible();
      await expect(page.getByTestId('scanner-setup-step')).toHaveText(/Step 2 of 2/i);
      await expect(
        page.locator('[data-testid="scanner-setup-dots"] [data-filled="true"]'),
      ).toHaveCount(0);

      await tapSetupDigits(page, pin);
      await expect(setup).toBeHidden({ timeout: 30_000 });

      await expect(page.getByTestId('warehouse-floor-shell')).toBeVisible();
      const badge = page.getByTestId('network-status-badge');
      await expect(badge).toBeVisible({ timeout: 15_000 });
      await expect(badge).toHaveAttribute('data-phase', 'online');
      await expect(badge).toContainText('Connected');

      await expect
        .poll(async () => {
          return page.evaluate(() => {
            const hook = (
              window as Window & {
                __INVSYS_SCANNER_LOCK__?: { lockDevice: () => void };
              }
            ).__INVSYS_SCANNER_LOCK__;
            return !!hook?.lockDevice;
          });
        }, { timeout: 10_000 })
        .toBe(true);

      await page.evaluate(() => {
        (
          window as Window & { __INVSYS_SCANNER_LOCK__?: { lockDevice: () => void } }
        ).__INVSYS_SCANNER_LOCK__?.lockDevice();
      });
      await expect(page.getByTestId('scanner-lock-overlay')).toBeVisible({ timeout: 10_000 });
      await tapUnlockDigits(page, pin);
      await expect(page.getByTestId('scanner-lock-overlay')).toBeHidden({ timeout: 20_000 });
      await expect(badge).toBeVisible();
      await expect(badge).toContainText('Connected');
    } finally {
      await close();
    }
  });

  test('PIN mismatch on confirm restarts enrollment', async ({ browser }) => {
    const { page, close } = await loginPickerFresh(browser);
    try {
      await clearScannerPinState(page);
      await page.goto('/fulfillment');

      const setup = page.getByTestId('scanner-pin-setup-overlay');
      await expect(setup).toBeVisible({ timeout: 25_000 });

      await tapSetupDigits(page, '1234');
      await expect(page.getByText('Confirm PIN')).toBeVisible({ timeout: 5_000 });
      await tapSetupDigits(page, '9999');

      await expect(page.getByText('Set shift PIN')).toBeVisible({ timeout: 8_000 });
      await expect(setup).toHaveAttribute('data-phase', 'create');
      await expect(setup).toBeVisible();
    } finally {
      await close();
    }
  });
});
