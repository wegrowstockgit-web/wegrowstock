import { expect, test } from '../fixtures/roleFixture';
import { completeScannerPin, dismissOnboardingTourIfPresent } from '../fixtures/roleFixture';
import { apiJson, contextForRole } from './helpers';

/**
 * Real functional E2E for Step 4 Support Co-Pilot:
 * proactive insights API, amber resolve-holds pill, HITL Action Draft approve/dismiss.
 */
test.describe('Support proactive insights & action drafts (functional)', () => {
  test.setTimeout(180_000);

  test('insights API returns proactiveInsight for sales-orders route', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const data = await apiJson<{ ok?: boolean; proactiveInsight?: string | null }>(
        manager.page,
        '/api/v1/support/insights?route=/sales-orders',
      );
      expect(data.ok).toBe(true);
      expect(data).toHaveProperty('proactiveInsight');
      // Demo seed often has hold/backorder traffic; when present, copy must be operator-facing.
      if (data.proactiveInsight) {
        expect(data.proactiveInsight).toMatch(/Credit Hold|BACKORDERED|attention|wave/i);
      }
    } finally {
      await manager.close();
    }
  });

  test('manager can open sales-orders insight pill and ask how to resolve holds', async ({
    browser,
  }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await expect(manager.page.getByTestId('support-assistant-fab')).toBeVisible({
        timeout: 30_000,
      });

      const fabInsight = manager.page.getByTestId('support-proactive-insight');
      if (await fabInsight.isVisible().catch(() => false)) {
        await expect(fabInsight).toContainText(/Credit Hold|BACKORDERED|attention|wave|💡/i);
        await fabInsight.click();
      } else {
        await manager.page.getByTestId('support-assistant-fab').click();
      }

      await expect(manager.page.getByTestId('support-assistant-panel')).toBeVisible();

      // Prefer panel insight auto-query; otherwise send the resolve-holds prompt explicitly.
      const panelInsight = manager.page.getByTestId('support-proactive-insight-panel');
      const alreadyStreaming = await manager.page
        .getByTestId('support-assistant-reply')
        .count()
        .then((n) => n > 0)
        .catch(() => false);
      if (!alreadyStreaming) {
        if (await panelInsight.isVisible().catch(() => false)) {
          await panelInsight.click();
        } else {
          await manager.page
            .getByTestId('support-assistant-input')
            .fill('How do I resolve these holds?');
          await manager.page.getByTestId('support-assistant-send').click();
        }
      }

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/hold|order|allocate|Diagnosis|Action|credit/i, {
        timeout: 45_000,
      });
      await expect(reply).not.toContainText(/\/api\/v1|CQRS|SupportChatService/i);
    } finally {
      await manager.close();
    }
  });

  test('unallocate question yields Action Draft that can be dismissed', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      const payload = await apiJson<
        { items?: Array<{ id?: string; number?: string }>; content?: Array<{ id?: string; number?: string }> } | Array<{ id?: string; number?: string }>
      >(manager.page, '/api/v1/sales-orders?page=1&size=10');
      const orders = Array.isArray(payload) ? payload : (payload.items ?? payload.content ?? []);
      const order = orders.find((o) => o.id || o.number);

      await manager.page.goto('/sales-orders');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      const question = order?.number
        ? `Please unallocate reserved stock on ${order.number}`
        : 'Please unallocate reserved stock on this order';
      await manager.page.getByTestId('support-assistant-input').fill(question);
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/Un-allocate|allocate|Diagnosis|Action/i, {
        timeout: 45_000,
      });

      const draft = manager.page.getByTestId('support-action-draft');
      await expect(draft).toBeVisible({ timeout: 20_000 });
      await expect(draft).toContainText(/Un-allocate|allocate/i);
      await expect(manager.page.getByTestId('support-draft-approve')).toBeVisible();
      const dismiss = manager.page.getByTestId('support-draft-cancel');
      await expect(dismiss).toHaveText(/Dismiss|Cancel/i);

      await dismiss.click();
      await expect(draft).toHaveCount(0);
    } finally {
      await manager.close();
    }
  });

  test('cycle-count Action Draft approve path soft-fails or executes cleanly', async ({
    browser,
  }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/cycle-counts');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await manager.page
        .getByTestId('support-assistant-input')
        .fill('Generate cycle count for zone Aisle-4 — do it for me');
      await manager.page.getByTestId('support-assistant-send').click();

      await expect(manager.page.getByTestId('support-action-draft')).toBeVisible({
        timeout: 25_000,
      });
      await manager.page.getByTestId('support-draft-approve').click();

      await expect(
        manager.page.getByTestId('support-draft-approved').first()
          .or(manager.page.getByTestId('support-draft-failed').first())
          .or(manager.page.getByTestId('support-draft-executed-badge').first()),
      ).toBeVisible({ timeout: 25_000 });
    } finally {
      await manager.close();
    }
  });
});
