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
 * Isolated browser context + fresh API login for a single test block.
 * Each fixture invocation opens its own context (no shared cookies/localStorage).
 * Demo-tenant journeys seed unique SKUs/SOs rather than a new Postgres database —
 * signup-per-test would drop seed data (WIDGET-S, picker@demo.test) that floor journeys need.
 */
async function pageForRole(
  browser: Browser,
  role: string,
  testId?: string,
): Promise<{ page: Page; close: () => Promise<void> }> {
  const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
  const email = ROLE_EMAIL[role];
  if (!email) {
    throw new Error(`Unknown role fixture: ${role}`);
  }

  const context: BrowserContext = await browser.newContext({
    baseURL,
    // Never reuse another test's storageState — isolation is the context itself.
  });
  const page = await context.newPage();
  installAutoUnlockNavigations(page);

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
      // Prevent onboarding / multi-page tours from covering PIN pads and grids in e2e.
      localStorage.setItem(
        'invsys-preferences',
        JSON.stringify({
          state: {
            densityMode: 'cozy',
            showOnboardingTour: false,
            activeTourId: null,
            currentTourStep: 0,
            isTourMovingRoutes: false,
            targetRoute: null,
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
    await context.storageState({
      path: testId ? path.join(AUTH_DIR, `${role}-${testId}.json`) : storageStateFor(role),
    });
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

const DEMO_PASSWORD_DEFAULT = process.env.E2E_DEMO_PASSWORD ?? 'password123';

/** Identifier-first login: email → Continue (HRD) → password → Sign in. */
export async function completeIdentifierFirstLogin(
  page: Page,
  email: string,
  password = DEMO_PASSWORD_DEFAULT,
): Promise<void> {
  await expect(page.getByTestId('login-email')).toBeVisible({ timeout: 30_000 });
  await page.getByTestId('login-email').fill(email);
  await expect(page.getByTestId('login-continue')).toBeVisible();
  await page.getByTestId('login-continue').click();
  await expect(page.getByTestId('login-password')).toBeVisible({ timeout: 20_000 });
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
}

/**
 * UI login for specs that don't use pageForRole / contextForRole.
 * Disables onboarding tour prefs and unlocks the scanner PIN gate.
 */
export async function loginAsDemo(
  page: Page,
  email = 'owner@demo.test',
  password = DEMO_PASSWORD_DEFAULT,
): Promise<void> {
  installAutoUnlockNavigations(page);
  await page.goto('/login');
  const retry = page.getByRole('button', { name: 'Retry' });
  if (await retry.isVisible({ timeout: 1_500 }).catch(() => false)) {
    await retry.click();
    await page.goto('/login');
  }
  await page.evaluate(() => {
    localStorage.setItem(
      'invsys-preferences',
      JSON.stringify({
        state: {
          densityMode: 'cozy',
          showOnboardingTour: false,
          activeTourId: null,
          currentTourStep: 0,
          isTourMovingRoutes: false,
          targetRoute: null,
        },
        version: 0,
      }),
    );
  });
  // Reload so Zustand persist rehydrates showOnboardingTour=false (memory otherwise stays true).
  await page.reload();
  await completeIdentifierFirstLogin(page, email, password);
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 45_000 });
  await completeScannerPin(page);
  await dismissOnboardingTourIfPresent(page);
}

/** Close the interactive-tour prompt if it is covering keypad / shell UI. */
export async function dismissOnboardingTourIfPresent(page: Page): Promise<void> {
  const dontShow = page.getByTestId('tour-dont-show');
  if (await dontShow.isVisible({ timeout: 1_500 }).catch(() => false)) {
    await dontShow.click();
    await expect(page.getByTestId('onboarding-tour-prompt')).toBeHidden({ timeout: 5_000 });
  }
  const skip = page.getByTestId('tour-skip');
  if (await skip.isVisible({ timeout: 500 }).catch(() => false)) {
    await skip.click();
  }
}

type ScannerGateSnapshot = {
  hydrated: boolean;
  isLocked: boolean;
  needsPinSetup: boolean;
  pinConfigured: boolean;
};

async function readScannerGate(page: Page): Promise<ScannerGateSnapshot | null> {
  return page.evaluate(() => {
    const hook = (
      window as Window & {
        __INVSYS_SCANNER_LOCK__?: { getState?: () => ScannerGateSnapshot };
      }
    ).__INVSYS_SCANNER_LOCK__;
    return hook?.getState?.() ?? null;
  });
}

/**
 * Enroll or unlock the 4-digit shift PIN after session hydration.
 * Safe to call after every full navigation: SPA remount wipes the in-memory crypto key,
 * so hydrate re-locks until PIN unlock (IndexedDB verifier still present).
 */
export async function completeScannerPin(page: Page, pin = '1234'): Promise<void> {
  await page
    .getByText('Loading session...')
    .waitFor({ state: 'detached', timeout: 30_000 })
    .catch(() => undefined);

  const path = new URL(page.url()).pathname;
  if (path.includes('/login') || path.includes('/signup') || path.includes('/invite')) {
    return;
  }
  const authed = await page.evaluate(() => {
    try {
      const raw = localStorage.getItem('invsys-session');
      if (!raw) return false;
      const parsed = JSON.parse(raw) as { state?: { authenticated?: boolean } };
      return !!parsed.state?.authenticated;
    } catch {
      return false;
    }
  });
  if (!authed) return;

  // Kill tour preference before overlays race the PIN pad (journeys often skip pageForRole prefs).
  await page.evaluate(() => {
    try {
      const raw = localStorage.getItem('invsys-preferences');
      const parsed = raw ? (JSON.parse(raw) as { state?: Record<string, unknown>; version?: number }) : { state: {}, version: 0 };
      parsed.state = {
        ...(parsed.state ?? {}),
        showOnboardingTour: false,
        activeTourId: null,
        currentTourStep: 0,
        isTourMovingRoutes: false,
        targetRoute: null,
      };
      localStorage.setItem('invsys-preferences', JSON.stringify(parsed));
    } catch {
      /* ignore */
    }
  });
  await dismissOnboardingTourIfPresent(page);

  // Wait until ScannerSecurityGate has hydrated (overlays only mount after this).
  await expect
    .poll(
      async () => {
        await dismissOnboardingTourIfPresent(page);
        const gate = await readScannerGate(page);
        return gate?.hydrated === true;
      },
      { timeout: 25_000 },
    )
    .toBe(true);

  const setup = page.getByTestId('scanner-pin-setup-overlay');
  const lock = page.getByTestId('scanner-lock-overlay');

  // Paint tick for overlays after hydrate.
  await expect
    .poll(
      async () => {
        await dismissOnboardingTourIfPresent(page);
        const gate = await readScannerGate(page);
        if (!gate) return 'waiting';
        if (gate.needsPinSetup || (await setup.isVisible().catch(() => false))) return 'setup';
        if (gate.isLocked || (await lock.isVisible().catch(() => false))) return 'lock';
        return 'ready';
      },
      { timeout: 15_000 },
    )
    .toMatch(/setup|lock|ready/);

  await dismissOnboardingTourIfPresent(page);

  const clickDigit = async (testId: string) => {
    await dismissOnboardingTourIfPresent(page);
    await page.getByTestId(testId).click({ force: true, timeout: 10_000 });
  };

  // Office routes no longer show the PIN UI — enroll silently so offline crypto still works for e2e.
  const gateAfter = await readScannerGate(page);
  const setupVisible = await setup.isVisible().catch(() => false);
  if (gateAfter?.needsPinSetup && !setupVisible) {
    const enrolled = await page.evaluate(async (p) => {
      const hook = (
        window as Window & {
          __INVSYS_SCANNER_LOCK__?: { setupPin: (pin: string) => Promise<void> };
        }
      ).__INVSYS_SCANNER_LOCK__;
      if (!hook?.setupPin) return false;
      await hook.setupPin(p);
      return true;
    }, pin);
    if (enrolled) {
      await expect
        .poll(async () => (await readScannerGate(page))?.needsPinSetup === false, {
          timeout: 15_000,
        })
        .toBe(true);
    }
  } else if (setupVisible) {
    for (const digit of pin) {
      await clickDigit(`scanner-setup-digit-${digit}`);
    }
    await expect(setup.getByText('Confirm PIN')).toBeVisible({ timeout: 8_000 });
    await dismissOnboardingTourIfPresent(page);
    for (const digit of pin) {
      await clickDigit(`scanner-setup-digit-${digit}`);
    }
    await expect(setup).toBeHidden({ timeout: 30_000 });
  } else if (await lock.isVisible().catch(() => false)) {
    for (const digit of pin) {
      await clickDigit(`scanner-unlock-digit-${digit}`);
    }
    await expect(lock).toBeHidden({ timeout: 30_000 });
  }

  // Full navigations can race a second hydrate/lock; clear once more if needed.
  if (await lock.isVisible({ timeout: 1_000 }).catch(() => false)) {
    for (const digit of pin) {
      await clickDigit(`scanner-unlock-digit-${digit}`);
    }
    await expect(lock).toBeHidden({ timeout: 30_000 });
  }

  await expect(setup).toBeHidden({ timeout: 5_000 });
  await expect(lock).toBeHidden({ timeout: 5_000 });
}

/**
 * Wrap page.goto / reload so full navigations auto-unlock the scanner PIN gate.
 * Client-side React Router clicks do not remount the app and keep the crypto key.
 */
/** Peek the decrypted offline mutation queue (requires PIN unlock + test hook). */
export async function peekMutationQueue(
  page: Page,
): Promise<Array<{ body?: Record<string, unknown> }>> {
  return page.evaluate(async () => {
    const hook = (
      window as Window & {
        __INVSYS_MUTATION_QUEUE__?: { peek: () => Promise<Array<{ body?: Record<string, unknown> }>> };
      }
    ).__INVSYS_MUTATION_QUEUE__;
    if (!hook?.peek) return [];
    return hook.peek();
  });
}

export function installAutoUnlockNavigations(page: Page): void {
  const marked = page as Page & { __invsysAutoUnlock?: boolean };
  if (marked.__invsysAutoUnlock) return;
  marked.__invsysAutoUnlock = true;

  const originalGoto = page.goto.bind(page);
  page.goto = async (url, options) => {
    const result = await originalGoto(url, options);
    await completeScannerPin(page);
    return result;
  };

  const originalReload = page.reload.bind(page);
  page.reload = async (options) => {
    const result = await originalReload(options);
    await completeScannerPin(page);
    return result;
  };
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
 * Role-authenticated page fixtures: new BrowserContext + login per test block.
 */
export const test = base.extend<RoleFixtures>({
  ownerPage: async ({ browser }, use, testInfo) => {
    const { page, close } = await pageForRole(browser, 'owner', testInfo.testId);
    await use(page);
    await close();
  },
  adminPage: async ({ browser }, use, testInfo) => {
    const { page, close } = await pageForRole(browser, 'admin', testInfo.testId);
    await use(page);
    await close();
  },
  managerPage: async ({ browser }, use, testInfo) => {
    const { page, close } = await pageForRole(browser, 'manager', testInfo.testId);
    await use(page);
    await close();
  },
  pickerPage: async ({ browser }, use, testInfo) => {
    const { page, close } = await pageForRole(browser, 'picker', testInfo.testId);
    await use(page);
    await close();
  },
  viewerPage: async ({ browser }, use, testInfo) => {
    const { page, close } = await pageForRole(browser, 'viewer', testInfo.testId);
    await use(page);
    await close();
  },
  b2bPage: async ({ browser }, use, testInfo) => {
    const { page, close } = await pageForRole(browser, 'b2b', testInfo.testId);
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
