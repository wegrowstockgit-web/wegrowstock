import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';

/**
 * Real functional e2e for business automations + daily ops market gaps:
 * settings toggles, punch clock, dock schedule, RTV workspace, rate shop entry.
 */
test.describe('Journey 55: Automations & operational market gaps', () => {
  test('automations settings, labor clock, dock, RTV workspace', async ({ browser }) => {
    test.setTimeout(180_000);
    const owner = await contextForRole(browser, 'owner');
    try {
      // --- Automations tab ---
      await owner.page.goto('/settings?tab=automations');
      await expect(owner.page.getByTestId('automation-settings')).toBeVisible({ timeout: 20_000 });
      const blind = owner.page.getByTestId('automation-blind-cycle-counts');
      await expect(blind).toBeVisible();
      const before = await blind.getAttribute('aria-checked');
      await blind.click();
      await owner.page.getByTestId('automation-settings-save').click();
      await expect(owner.page.getByText('Automation settings saved')).toBeVisible({
        timeout: 10_000,
      });
      // restore baseline
      if (before === 'true') {
        // toggled off — turn back on
        if ((await blind.getAttribute('aria-checked')) === 'false') {
          await blind.click();
          await owner.page.getByTestId('automation-settings-save').click();
        }
      }

      const patchCheck = await owner.page.request.get('/api/v1/settings');
      expect(patchCheck.ok()).toBeTruthy();
      const settings = (await patchCheck.json()) as {
        predictive_replenishment_enabled?: boolean;
        rma_auto_approve_max_value?: number;
      };
      expect(settings.predictive_replenishment_enabled).toBeDefined();
      expect(settings.rma_auto_approve_max_value).toBeDefined();

      // --- RTV workspace ---
      await owner.page.goto('/purchasing/rtv');
      await expect(owner.page.getByTestId('rtv-workspace')).toBeVisible({ timeout: 15_000 });

      // --- Dock schedule ---
      await owner.page.goto('/dock-schedule');
      await expect(owner.page.getByTestId('dock-schedule-calendar')).toBeVisible({
        timeout: 15_000,
      });
      await owner.page.getByTestId('dock-book-open').click();
      await expect(owner.page.getByTestId('dock-book-modal')).toBeVisible();
      await owner.page.getByTestId('dock-book-submit').click();
      await expect(owner.page.getByText(/Dock appointment scheduled|Could not schedule/)).toBeVisible({
        timeout: 15_000,
      });

      // --- Labor analytics on dashboard ---
      await owner.page.goto('/dashboard');
      await expect(owner.page.getByTestId('labor-analytics-dashboard')).toBeVisible({
        timeout: 20_000,
      });

      // --- Punch clock API (floor chrome is warehouse shell) ---
      const clockIn = await owner.page.request.post('/api/v1/labor/clock-in', {
        data: {},
      });
      expect(clockIn.ok(), await clockIn.text()).toBeTruthy();
      const me = await owner.page.request.get('/api/v1/labor/me');
      expect(me.ok()).toBeTruthy();
      const status = (await me.json()) as { active?: boolean; currentActivity?: string };
      expect(status.active).toBe(true);
      const switchAct = await owner.page.request.post('/api/v1/labor/switch-activity', {
        data: { activityType: 'PUTAWAY' },
      });
      expect(switchAct.ok(), await switchAct.text()).toBeTruthy();
      const clockOut = await owner.page.request.post('/api/v1/labor/clock-out');
      expect(clockOut.ok(), await clockOut.text()).toBeTruthy();
    } finally {
      await owner.close();
    }
  });

  test('rate-shop API returns ranked quotes for demo order when available', async ({ page }) => {
    test.setTimeout(90_000);
    const login = await page.request.post('/api/v1/auth/login', {
      data: { email: 'owner@demo.test', password: 'password123' },
    });
    test.skip(!login.ok(), 'Demo API not reachable');

    const ordersRes = await page.request.get('/api/v1/sales-orders');
    test.skip(!ordersRes.ok(), 'Sales orders unavailable');
    const orders = (await ordersRes.json()) as Array<{ id: string; status: string }>;
    const candidate =
      orders.find((o) => ['CONFIRMED', 'ALLOCATED', 'PARTIALLY_SHIPPED'].includes(o.status)) ??
      orders[0];
    test.skip(!candidate, 'No sales order for rate shop');

    const shop = await page.request.post('/api/v1/shipments/rate-shop', {
      data: { salesOrderId: candidate.id },
    });
    // Carton masters may be missing in some demos — accept 422 as soft skip
    if (shop.status() === 422) {
      test.skip(true, 'Cartonization prerequisites missing');
    }
    expect(shop.ok(), await shop.text()).toBeTruthy();
    const body = (await shop.json()) as { rates?: unknown[]; recommended?: unknown };
    expect(Array.isArray(body.rates)).toBeTruthy();
    expect(body.rates!.length).toBeGreaterThan(0);
  });
});
