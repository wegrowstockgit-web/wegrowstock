import path from 'node:path';
import { expect, test as base, type Page, type Browser, type BrowserContext } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
const AUTH_DIR = path.join(process.cwd(), 'playwright', '.auth');

const ROLE_EMAIL: Record<string, string> = {
  owner: 'owner@demo.test',
  admin: 'admin@demo.test',
  manager: 'manager@demo.test',
  picker: 'picker@demo.test',
  viewer: 'viewer@demo.test',
  b2b: 'b2b@demo.test',
};

function storageStateFor(role: string): string {
  return path.join(AUTH_DIR, `${role}.json`);
}

/**
 * Isolated context with a fresh API login.
 * Prefer this over cached storageState — refresh tokens rotate and go stale mid-suite.
 */
async function pageForRole(
  browser: Browser,
  role: string,
): Promise<{ page: Page; close: () => Promise<void> }> {
  const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
  const email = ROLE_EMAIL[role];
  if (!email) {
    throw new Error(`Unknown role fixture: ${role}`);
  }

  const context: BrowserContext = await browser.newContext({ baseURL });
  const page = await context.newPage();

  let loginRes = await page.request.post('/api/v1/auth/login', {
    data: { email, password: DEMO_PASSWORD },
  });
  for (let attempt = 0; !loginRes.ok() && loginRes.status() === 429 && attempt < 4; attempt += 1) {
    await page.waitForTimeout(15_000 * (attempt + 1));
    loginRes = await page.request.post('/api/v1/auth/login', {
      data: { email, password: DEMO_PASSWORD },
    });
  }
  if (!loginRes.ok()) {
    const status = loginRes.status();
    const body = await loginRes.text().catch(() => '');
    await context.close();
    throw new Error(`Login failed for ${email}: ${status} ${body}`);
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
        email: me?.email ?? email,
        displayName: me?.displayName ?? email,
        roles: me?.roles ?? session.roles,
        warehouseIds: me?.warehouseIds ?? session.warehouseIds ?? [],
        tenantId: me?.tenantId ?? session.tenantId,
      },
    },
  );
  await page.goto('/dashboard');
  // Wait until the office shell settles (avoids racing API restart / cookie hydration).
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 45_000 }).catch(() => {});
  if (page.url().includes('/login')) {
    await page.goto('/dashboard');
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 45_000 });
  }

  await completeScannerPin(page);

  // Keep a copy for debugging / optional reuse; do not rely on it as the sole session source.
  try {
    await context.storageState({ path: storageStateFor(role) });
  } catch {
    // ignore persistence failures
  }

  return {
    page,
    close: async () => {
      await context.close();
    },
  };
}

/**
 * Enroll or unlock the 4-digit shift PIN after session hydration.
 * Safe to call on every authenticated navigation.
 */
export async function completeScannerPin(page: Page, pin = '1234'): Promise<void> {
  await page
    .getByText('Loading session...')
    .waitFor({ state: 'detached', timeout: 30_000 })
    .catch(() => undefined);

  const setup = page.getByTestId('scanner-pin-setup-overlay');
  const lock = page.getByTestId('scanner-lock-overlay');

  // Wait for the security gate to hydrate into setup, lock, or already-unlocked.
  await expect
    .poll(
      async () => {
        if (await setup.isVisible().catch(() => false)) return 'setup';
        if (await lock.isVisible().catch(() => false)) return 'lock';
        return 'ready';
      },
      { timeout: 20_000 },
    )
    .toMatch(/setup|lock|ready/);

  if (await setup.isVisible().catch(() => false)) {
    for (const digit of pin) {
      await page.getByTestId(`scanner-setup-digit-${digit}`).click();
    }
    await expect(setup.getByText('Confirm PIN')).toBeVisible({ timeout: 8_000 });
    for (const digit of pin) {
      await page.getByTestId(`scanner-setup-digit-${digit}`).click();
    }
    await expect(setup).toBeHidden({ timeout: 30_000 });
    return;
  }

  if (await lock.isVisible().catch(() => false)) {
    for (const digit of pin) {
      await page.getByTestId(`scanner-unlock-digit-${digit}`).click();
    }
    await expect(lock).toBeHidden({ timeout: 30_000 });
  }
}

type RoleFixtures = {
  ownerPage: Page;
  adminPage: Page;
  managerPage: Page;
  pickerPage: Page;
  viewerPage: Page;
  b2bPage: Page;
};

/**
 * Role-authenticated page fixtures with fresh cookies per test.
 */
export const test = base.extend<RoleFixtures>({
  ownerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'owner');
    await use(page);
    await close();
  },
  adminPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'admin');
    await use(page);
    await close();
  },
  managerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'manager');
    await use(page);
    await close();
  },
  pickerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'picker');
    await use(page);
    await close();
  },
  viewerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'viewer');
    await use(page);
    await close();
  },
  b2bPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'b2b');
    await use(page);
    await close();
  },
});

export { expect };

/** Simulate a HID wedge barcode scan (fast keydown burst + Enter). */
export async function hidScan(page: Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    // Match production hook: window capture-phase keydown listeners.
    for (const key of code) {
      window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    }
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
  }, barcode);
}

/** Confirms Zustand session profile is present (JWTs live in HttpOnly cookies). */
export async function assertSession(page: Page): Promise<void> {
  const ok = await page.evaluate(() => {
    const raw = localStorage.getItem('invsys-session');
    if (!raw) return false;
    const parsed = JSON.parse(raw) as { state?: { authenticated?: boolean; user?: unknown } };
    return !!parsed.state?.authenticated && !!parsed.state?.user;
  });
  if (!ok) {
    throw new Error('No authenticated session profile in invsys-session storage');
  }
}

/**
 * @deprecated Prefer cookie-auth via storageState. Returns empty string — do not send Bearer.
 */
export async function sessionAccessToken(page: Page): Promise<string> {
  await assertSession(page);
  return '';
}
