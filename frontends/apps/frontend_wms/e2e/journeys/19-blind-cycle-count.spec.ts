import { expect, test } from '../fixtures/roleFixture';
import {
  contextForRole,
  PICK_BIN_B02_ID,
  PICK_BIN_ID,
  WIDGET_S_SKU,
} from './helpers';

async function resolveWidgetVariantId(page: import('@playwright/test').Page): Promise<string> {
  const res = await page.request.get('/api/v1/variants?limit=50');
  expect(res.ok(), await res.text()).toBeTruthy();
  const body = (await res.json()) as { items?: Array<{ id: string; sku: string }> };
  const widget = (body.items ?? []).find((v) => v.sku === WIDGET_S_SKU);
  expect(widget, 'WIDGET-S variant').toBeTruthy();
  return widget!.id;
}

type CountDetail = {
  id: string;
  blindCycleCounts: boolean;
  lines: Array<{
    id: string;
    sku: string;
    expectedQty: number | string;
    varianceStatus: string;
  }>;
};

async function openCount(
  page: import('@playwright/test').Page,
  countId: string,
): Promise<CountDetail> {
  const res = await page.request.post(`/api/v1/cycle-counts/${countId}/open`);
  expect(res.ok(), await res.text()).toBeTruthy();
  return (await res.json()) as CountDetail;
}

/** Match-submit every non-target PENDING line so the scanner lands on the target SKU. */
async function clearOtherPendingLines(
  page: import('@playwright/test').Page,
  detail: CountDetail,
  targetLineId: string,
) {
  for (const line of detail.lines) {
    if (line.id === targetLineId) continue;
    if (line.varianceStatus !== 'PENDING' && line.varianceStatus !== 'RECOUNT_REQUESTED') continue;
    const expected = Math.max(0, Number(line.expectedQty));
    if (!Number.isFinite(expected)) continue;
    const res = await page.request.post(
      `/api/v1/cycle-counts/${detail.id}/lines/${line.id}/submit`,
      { data: { countedQty: expected } },
    );
    expect(res.ok(), await res.text()).toBeTruthy();
  }
}

async function prepareCountAt(
  page: import('@playwright/test').Page,
  locationId: string,
  quantity: number,
): Promise<{ countId: string; lineId: string; detail: CountDetail }> {
  const variantId = await resolveWidgetVariantId(page);
  const receive = await page.request.post('/api/v1/inventory/receive', {
    data: {
      variantId,
      locationId,
      quantity,
      referenceType: 'E2E_BLIND_COUNT',
    },
  });
  expect(receive.ok(), await receive.text()).toBeTruthy();

  const start = await page.request.post('/api/v1/cycle-counts', {
    data: { locationId },
  });
  expect(start.ok(), await start.text()).toBeTruthy();
  let detail = (await start.json()) as CountDetail;
  detail = await openCount(page, detail.id);

  const pendingLine =
    detail.lines.find(
      (l) =>
        l.sku === WIDGET_S_SKU &&
        Number(l.expectedQty) > 0 &&
        (l.varianceStatus === 'PENDING' || l.varianceStatus === 'RECOUNT_REQUESTED'),
    ) ??
    detail.lines.find(
      (l) =>
        Number(l.expectedQty) > 0 &&
        (l.varianceStatus === 'PENDING' || l.varianceStatus === 'RECOUNT_REQUESTED'),
    );
  expect(pendingLine, 'pending countable line').toBeTruthy();
  await clearOtherPendingLines(page, detail, pendingLine!.id);
  return { countId: detail.id, lineId: pendingLine!.id, detail };
}

/**
 * Journey 19 — Blind cycle counting + automated variance escalation.
 */
test.describe('Journey 19: Blind Cycle Count & Variance Escalation', () => {
  test.setTimeout(240_000);

  test('blind scanner hides expected qty; over-threshold variance reaches manager desk', async ({
    browser,
  }) => {
    let countId = '';
    let lineId = '';

    const owner = await contextForRole(browser, 'owner');
    try {
      const patch = await owner.page.request.patch('/api/v1/settings', {
        data: {
          blind_cycle_counts: true,
          max_auto_adjust_value: 100,
        },
      });
      expect(patch.ok(), await patch.text()).toBeTruthy();

      // avg_cost $8 → 20 units zero-count = $160 impact (> $100)
      const prepared = await prepareCountAt(owner.page, PICK_BIN_B02_ID, 20);
      countId = prepared.countId;
      lineId = prepared.lineId;
      expect(prepared.detail.blindCycleCounts).toBe(true);
    } finally {
      await owner.close();
    }

    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.goto('/cycle-counts');
      await expect(picker.page.getByRole('heading', { name: 'Cycle counts' })).toBeVisible({
        timeout: 30_000,
      });

      await picker.page.getByTestId(`open-count-${countId}`).click();
      await expect(picker.page.getByTestId('cycle-count-scanner')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('cycle-count-scanner')).toHaveAttribute(
        'data-blind',
        'true',
      );
      await expect(picker.page.getByTestId('cycle-count-expected')).toHaveCount(0);
      await expect(picker.page.getByTestId('cycle-count-prompt')).toContainText(
        'Enter total physical quantity for SKU',
      );
      await expect(picker.page.getByTestId('cycle-count-confirm-match')).toHaveCount(0);

      await picker.page.getByTestId('cycle-count-key-0').click();
      const submitWait = picker.page.waitForResponse(
        (res) =>
          res.url().includes(`/api/v1/cycle-counts/${countId}/lines/`) &&
          res.url().includes('/submit') &&
          res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await picker.page.getByTestId('cycle-count-submit').click();
      const submitRes = await submitWait;
      expect(submitRes.ok(), await submitRes.text()).toBeTruthy();
      const submitted = (await submitRes.json()) as { varianceStatus: string };
      expect(submitted.varianceStatus).toBe('PENDING_MANAGER_REVIEW');
    } finally {
      await picker.close();
    }

    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/cycle-counts');
      await expect(manager.page.getByTestId('pending-variances-card')).toBeVisible({
        timeout: 30_000,
      });

      const row = manager.page.getByTestId(`pending-variance-${lineId}`);
      await expect(row).toBeVisible({ timeout: 20_000 });
      await expect(row.getByTestId(`financial-delta-${lineId}`)).toContainText('$');

      const approveWait = manager.page.waitForResponse(
        (res) =>
          res.url().includes(`/api/v1/cycle-counts/lines/${lineId}/approve-adjustment`) &&
          res.request().method() === 'POST',
        { timeout: 30_000 },
      );
      await row.getByRole('button', { name: 'Approve Ledger Adjustment' }).click();
      const approveRes = await approveWait;
      expect(approveRes.ok(), await approveRes.text()).toBeTruthy();
      await expect(row).toHaveCount(0, { timeout: 20_000 });
    } finally {
      await manager.close();
    }
  });

  test('non-blind mode shows expected qty and Confirm Match', async ({ browser }) => {
    let countId = '';
    const owner = await contextForRole(browser, 'owner');
    try {
      const patch = await owner.page.request.patch('/api/v1/settings', {
        data: { blind_cycle_counts: false, max_auto_adjust_value: 100 },
      });
      expect(patch.ok(), await patch.text()).toBeTruthy();

      const prepared = await prepareCountAt(owner.page, PICK_BIN_ID, 5);
      countId = prepared.countId;

      await owner.page.goto('/cycle-counts');
      await expect(owner.page.getByRole('heading', { name: 'Cycle counts' })).toBeVisible({
        timeout: 30_000,
      });
      await owner.page.getByTestId(`open-count-${countId}`).click();

      await expect(owner.page.getByTestId('cycle-count-scanner')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('cycle-count-scanner')).toHaveAttribute(
        'data-blind',
        'false',
      );
      await expect(owner.page.getByTestId('cycle-count-expected')).toBeVisible();
      await expect(owner.page.getByTestId('cycle-count-confirm-match')).toBeVisible();
    } finally {
      await owner.page.request.patch('/api/v1/settings', {
        data: { blind_cycle_counts: true, max_auto_adjust_value: 100 },
      });
      await owner.close();
    }
  });
});
