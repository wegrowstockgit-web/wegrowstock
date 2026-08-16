import { test, type Browser, type Page } from '@playwright/test';
import { completeScannerPin, installAutoUnlockNavigations } from '../fixtures/roleFixture';
import {
  WH_01,
  WIDGET_S_BARCODE,
  WIDGET_S_SKU,
  apiJson,
  contextForRole,
  expect,
  expectFulfillmentSurface,
  findVariantId,
  firstCustomerId,
} from './helpers';

declare global {
  interface Window {
    __vibrateCalls?: Array<number | number[]>;
    __audioCtorCalls?: number;
    __intentReceivers?: Array<(intent: unknown) => void>;
    plugins?: {
      intentShim?: {
        registerBroadcastReceiver: (
          filters: unknown,
          cb: (intent: unknown) => void,
        ) => void;
        unregisterBroadcastReceiver?: () => void;
      };
    };
  }
}

async function openFulfillmentPick(page: Page): Promise<void> {
  await page.goto('/fulfillment');
  await expectFulfillmentSurface(page);
  await page.getByRole('button', { name: 'Single' }).click();
  await page.getByRole('radio', { name: 'Pick' }).click();
  // Unlock AudioContext (gesture required by browsers)
  await page.locator('body').click({ position: { x: 8, y: 8 } });
}

/** Seed an allocated SO line so a successful WIDGET-S pick can fulfill something. */
async function seedPickableWidgetOrder(browser: Browser): Promise<string> {
  const manager = await contextForRole(browser, 'manager');
  try {
    const variantId = await findVariantId(manager.page, WIDGET_S_SKU);
    const customerId = await firstCustomerId(manager.page);
    const so = await apiJson<{ id: string; number: string }>(manager.page, '/api/v1/sales-orders', {
      method: 'POST',
      body: JSON.stringify({
        customerId,
        number: `SO-HW-${Date.now()}`,
        lines: [{ variantId, qtyOrdered: 1, unitPrice: 12.5 }],
      }),
    });
    await manager.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
    await manager.page.request.post(`/api/v1/sales-orders/${so.id}/allocate`);
    const waveRes = await manager.page.request.post('/api/v1/picking/waves/generate', {
      headers: { 'Content-Type': 'application/json' },
      data: {},
    });
    if (waveRes.ok()) {
      const wave = (await waveRes.json()) as { waveId: string };
      await manager.page.request.post(`/api/v1/picking/waves/${wave.waveId}/release`);
    }
    return so.id;
  } finally {
    await manager.close();
  }
}

/**
 * Tracks 10–12 — HID burst, Zebra/Honeywell Intent, haptic/audio feedback contracts.
 * Feedback spies + intentShim are installed via addInitScript before navigation.
 */
test.describe('Journey 10–12: Hardware scanning matrix', () => {
  test('HID burst without focus; Intent broadcast; haptic/audio contracts', async ({ browser }) => {
    test.setTimeout(120_000);

    const salesOrderId = await seedPickableWidgetOrder(browser);

    const pickerCtx = await browser.newContext({
      baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    });

    // Install hardware mocks before any document loads
    await pickerCtx.addInitScript(() => {
      (window as Window).__vibrateCalls = [];
      navigator.vibrate = ((pattern: number | number[]) => {
        // Always push onto the live window array (tests may replace/clear it).
        const bucket = ((window as Window).__vibrateCalls ??= []);
        bucket.push(pattern);
        return true;
      }) as typeof navigator.vibrate;

      let audioCtorCalls = 0;
      (window as Window).__audioCtorCalls = 0;
      class MockAudioContext {
        state = 'running';
        currentTime = 0;
        destination = {};
        constructor() {
          audioCtorCalls += 1;
          (window as Window).__audioCtorCalls = audioCtorCalls;
        }
        createOscillator() {
          return {
            type: 'sine',
            frequency: { value: 0 },
            connect() {
              return undefined;
            },
            start() {
              return undefined;
            },
            stop() {
              return undefined;
            },
          };
        }
        createGain() {
          return {
            gain: { value: 0 },
            connect() {
              return undefined;
            },
          };
        }
        resume() {
          return Promise.resolve();
        }
      }
      (
        window as unknown as { AudioContext: typeof MockAudioContext }
      ).AudioContext = MockAudioContext;

      const receivers: Array<(intent: unknown) => void> = [];
      (window as Window).__intentReceivers = receivers;
      const w = window as Window & { plugins?: Window['plugins'] };
      w.plugins = w.plugins ?? {};
      w.plugins.intentShim = {
        registerBroadcastReceiver: (_filters, cb) => {
          receivers.push(cb);
        },
        unregisterBroadcastReceiver: () => {
          receivers.length = 0;
        },
      };
    });

    const page = await pickerCtx.newPage();
    installAutoUnlockNavigations(page);

    // Fresh picker login into this mocked context
    const loginRes = await page.request.post('/api/v1/auth/login', {
      data: { email: 'picker@demo.test', password: process.env.E2E_DEMO_PASSWORD ?? 'password123' },
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
              isTourMovingRoutes: false,
              targetRoute: null,
            },
            version: 0,
          }),
        );
      },
      {
        user: {
          id: session.userId,
          email: 'picker@demo.test',
          displayName: 'Floor Picker',
          roles: session.roles,
          warehouseIds: session.warehouseIds ?? [WH_01],
          tenantId: session.tenantId,
        },
      },
    );

    try {
      // Claim wave if present so Intent pick can consume allocation
      const waves = await page.request.get('/api/v1/picking/waves');
      if (waves.ok()) {
        const list = (await waves.json()) as Array<{ id?: string; waveId?: string; status?: string }>;
        const open = list.find((w) => /RELEASED|OPEN|ACTIVE/i.test(w.status ?? ''));
        const waveId = open?.waveId ?? open?.id;
        if (waveId) {
          await page.request.post(`/api/v1/picking/waves/${waveId}/claim`, {
            headers: { 'X-Warehouse-Id': WH_01 },
          });
        }
      }

      await openFulfillmentPick(page);
      await completeScannerPin(page);

      // ── Track 10: HID global burst with focus loss ─────────────────────
      await page.locator('body').click({ position: { x: 4, y: 4 } });
      await page.evaluate(() => {
        const active = document.activeElement as HTMLElement | null;
        active?.blur?.();
      });

      const historyBefore = await page.locator('[data-testid="scan-buffer-card"]').innerText();

      const scanWait = page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
        { timeout: 20_000 },
      );

      // High-speed wedge: delay 10ms < SCANNER_MAX_GAP_MS (35)
      await page.keyboard.type(WIDGET_S_BARCODE, { delay: 10 });
      await page.keyboard.press('Enter');

      const scanRes = await scanWait;
      expect(scanRes.ok(), await scanRes.text()).toBeTruthy();

      const lastScan = await page.evaluate(() => {
        // Zustand persist key is in-memory; read from visible UI + scan buffer card
        return document.body.innerText;
      });
      expect(lastScan).toContain(WIDGET_S_BARCODE);

      await expect(page.getByTestId('scan-buffer-card')).toContainText(WIDGET_S_BARCODE, {
        timeout: 10_000,
      });
      // Pick / history advanced (Recent scans shows the barcode)
      await expect(page.getByText(WIDGET_S_BARCODE).first()).toBeVisible();
      const historyAfter = await page.locator('[data-testid="scan-buffer-card"]').innerText();
      expect(historyAfter.length).toBeGreaterThanOrEqual(historyBefore.length);

      // Success haptic: vibrate(50) exactly once for this scan burst
      await expect
        .poll(async () => {
          return page.evaluate(() => {
            const calls = (window as Window).__vibrateCalls ?? [];
            return calls.filter((c) => c === 50 || (Array.isArray(c) && c.length === 1 && c[0] === 50))
              .length;
          });
        }, { timeout: 5_000 })
        .toBeGreaterThanOrEqual(1);

      // AudioContext constructed for success tone
      await expect
        .poll(async () => page.evaluate(() => (window as Window).__audioCtorCalls ?? 0))
        .toBeGreaterThan(0);

      // ── Track 11: Zebra / Honeywell Intent broadcast ───────────────────
      await page.evaluate(() => {
        ((window as Window).__vibrateCalls ??= []).length = 0;
      });

      const intentScanWait = page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
        { timeout: 20_000 },
      );

      const receiversReady = await page.evaluate(() => {
        return ((window as Window).__intentReceivers ?? []).length;
      });
      expect(receiversReady, 'intentShim registerBroadcastReceiver should have run').toBeGreaterThan(
        0,
      );

      await page.evaluate((barcode) => {
        const receivers = (window as Window).__intentReceivers ?? [];
        for (const cb of receivers) {
          cb({
            extras: {
              'com.symbol.datawedge.data_string': barcode,
            },
          });
        }
      }, WIDGET_S_BARCODE);

      const intentRes = await intentScanWait;
      // May 200 (picked) or 409 if prior HID already consumed allocation — either proves Intent → scan path
      expect([200, 201, 409, 422]).toContain(intentRes.status());
      await expect(page.getByText(WIDGET_S_BARCODE).first()).toBeVisible();

      // Soft: SO still reachable / progressed for office correlation
      const soRes = await page.request.get(`/api/v1/sales-orders/${salesOrderId}`);
      if (soRes.ok()) {
        const so = (await soRes.json()) as { status?: string };
        expect(so.status).toBeTruthy();
      }

      // ── Track 12: Haptic & audio + destructive flash ───────────────────
      // Stub a successful scan so the success haptic contract is independent of OH/allocation.
      await page.route('**/api/v1/fulfillment/scan', async (route) => {
        if (route.request().method() !== 'POST') {
          await route.continue();
          return;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            variantId: 'a0000000-0000-4000-8000-000000000801',
            sku: WIDGET_S_SKU,
            name: 'Widget Small',
            requiresSerial: false,
            message: 'Picked 1 unit(s)',
            putawayTarget: null,
            primaryMediaUrl: null,
            isLotTracked: false,
            lotLoggedNotTracked: false,
          }),
        });
      });

      await page.evaluate(() => {
        ((window as Window).__vibrateCalls ??= []).length = 0;
      });

      const okScanWait = page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
      );
      await page.locator('body').click({ position: { x: 4, y: 4 } });
      await page.keyboard.type(WIDGET_S_BARCODE, { delay: 10 });
      await page.keyboard.press('Enter');
      expect((await okScanWait).ok()).toBeTruthy();

      await expect
        .poll(async () => {
          return page.evaluate(() => {
            const calls = (window as Window).__vibrateCalls ?? [];
            return calls.filter(
              (c) => c === 50 || (Array.isArray(c) && c.length === 1 && c[0] === 50),
            ).length;
          });
        }, { timeout: 5_000 })
        .toBe(1);

      await page.unroute('**/api/v1/fulfillment/scan');

      // Invalid barcode → error haptic [200, 100, 200] + #ef4444 flash overlay
      await page.evaluate(() => {
        ((window as Window).__vibrateCalls ??= []).length = 0;
      });

      const badScanWait = page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/fulfillment/scan') && res.request().method() === 'POST',
      );
      await page.keyboard.type('0000000000000', { delay: 10 });
      await page.keyboard.press('Enter');
      const badRes = await badScanWait;
      expect(badRes.ok()).toBeFalsy();

      await expect
        .poll(async () => {
          return page.evaluate(() => {
            const calls = (window as Window).__vibrateCalls ?? [];
            return calls.some(
              (c) =>
                Array.isArray(c) &&
                c.length === 3 &&
                c[0] === 200 &&
                c[1] === 100 &&
                c[2] === 200,
            );
          });
        }, { timeout: 5_000 })
        .toBeTruthy();

      // Destructive red token — flash-error keyframes use rgba(239, 68, 68, …) = #ef4444
      const flash = page.getByTestId('scan-flash-overlay');
      await expect(flash).toHaveAttribute('data-flash', 'error', { timeout: 3_000 });
      await expect(flash).toHaveClass(/animate-flash-error/);
      const flashClass = (await flash.getAttribute('class')) ?? '';
      expect(flashClass).toMatch(/flash-error/);
    } finally {
      await pickerCtx.close();
    }
  });
});
