import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 30 — Dashboard command center: alerts, summary cards, deep-links.
 */
test.describe('Journey 30: Dashboard command center UX', () => {
  test.setTimeout(180_000);

  test('dashboard is glanceable and routes to exception/report workspaces', async ({
    browser,
  }) => {
    let owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 720 });
      await owner.page.goto('/dashboard');
      await expect(owner.page.getByTestId('floating-kpi-row')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByTestId('activity-feed')).toBeVisible();
      await expect(owner.page.getByTestId('labor-velocity-leaderboard')).toBeVisible();
      await expect(owner.page.getByTestId('recent-ledger-activity')).toBeVisible();

      // Main scrollport: native bar hidden; fold cue + scroll when content overflows.
      const mainScroll = owner.page.getByTestId('app-main-scroll');
      await expect(mainScroll).toBeVisible({ timeout: 10_000 });
      expect(
        await mainScroll.evaluate(
          (el) =>
            getComputedStyle(el).scrollbarWidth === 'none' ||
            el.classList.contains('scrollbar-none'),
        ),
      ).toBeTruthy();
      if (await mainScroll.evaluate((el) => el.scrollHeight > el.clientHeight + 40)) {
        await expect(owner.page.getByTestId('app-main-scroll-scroll-down')).toBeVisible({
          timeout: 5_000,
        });
        await mainScroll.evaluate((el) => {
          el.scrollTop = Math.min(el.scrollHeight - el.clientHeight, 320);
        });
        expect(await mainScroll.evaluate((el) => el.scrollTop)).toBeGreaterThan(40);
      }

      const stockText =
        (await owner.page.getByTestId('kpi-stock-value').locator('.tabular-nums').textContent()) ??
        '';
      expect(stockText).toMatch(/\$|[\d,]/);
      expect(stockText.includes('....')).toBeFalsy();

      // Heavy tables no longer live on the dashboard.
      await expect(owner.page.getByTestId('sync-conflicts-panel')).toHaveCount(0);
      await expect(owner.page.getByTestId('ledger-history-table')).toHaveCount(0);

      await owner.page.getByTestId('labor-velocity-view-full').click();
      await expect(owner.page).toHaveURL(/\/reports\?tab=labor/);
      await expect(owner.page.getByTestId('reports-labor-panel')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('labor-velocity-leaderboard')).toHaveAttribute(
        'data-mode',
        'full',
      );

      await owner.page.goto('/reports?tab=audit');
      await expect(owner.page.getByTestId('reports-audit-panel')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('ledger-history-table')).toBeVisible();

      await owner.page.goto('/exceptions?tab=sync');
      if (owner.page.url().includes('/login')) {
        // Mid-suite refresh-cookie rotation can drop the session; rebuild context once.
        await owner.close();
        const retry = await contextForRole(browser, 'owner');
        owner = retry;
        await owner.page.goto('/exceptions?tab=sync');
      }
      await expect(owner.page.getByRole('heading', { name: 'Action required' })).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.getByTestId('action-required-hub')).toBeVisible({ timeout: 10_000 });
      await expect(owner.page.getByTestId('exceptions-tab-sync')).toHaveAttribute(
        'aria-selected',
        'true',
      );
      await expect(owner.page.getByTestId('sync-conflicts-panel')).toBeVisible({ timeout: 20_000 });

      await owner.page.getByTestId('exceptions-tab-holds').click();
      await expect(owner.page.getByTestId('exceptions-tab-holds')).toHaveAttribute(
        'aria-selected',
        'true',
      );

      // Product peek ledger tab — only when the catalog has at least one variant.
      const variantsRes = await owner.page.request.get('/api/v1/variants?limit=1');
      expect(variantsRes.ok()).toBeTruthy();
      const variantsBody = (await variantsRes.json()) as {
        items?: Array<{ id: string; sku: string }>;
      };
      const first = variantsBody.items?.[0];
      if (first?.sku) {
        await owner.page.goto('/products');
        await expect(
          owner.page.getByRole('heading', { name: 'Products', exact: true }),
        ).toBeVisible({ timeout: 20_000 });
        await owner.page.getByText(first.sku, { exact: true }).first().click();
        await expect(owner.page.getByTestId('product-peek-tab-ledger')).toBeVisible({
          timeout: 15_000,
        });
        await owner.page.getByTestId('product-peek-tab-ledger').click();
        await expect(owner.page.getByTestId('ledger-history-table')).toBeVisible({
          timeout: 15_000,
        });
      }
    } finally {
      await owner.close();
    }
  });
});
