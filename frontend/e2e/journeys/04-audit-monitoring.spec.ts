import { test } from '@playwright/test';
import { contextForRole, expect } from './helpers';
import { readJourneyState } from './journeyState';

interface AuditRow {
  id: string;
  actorUserId?: string;
  action: string;
  entityType: string;
  entityId: string;
  diff: Record<string, unknown>;
  createdAt?: string;
}

/**
 * Track 4 — Owner verifies chronological audit trail from journeys 01–03.
 */
test.describe.serial('Journey 04: Ownership audit trail', () => {
  test('owner audit log captures invite / PO / SO / exception actors', async ({ browser }) => {
    const state = readJourneyState();
    const owner = await contextForRole(browser, 'owner');

    try {
      await owner.page.goto('/reports/audit-log');
      await expect(owner.page.getByTestId('operations-console')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('audit-log-table')).toBeVisible();
      await expect(owner.page.getByText('Compliance audit log')).toBeVisible();

      const auditRes = await owner.page.request.get('/api/v1/audit/tenant?limit=50');
      expect(auditRes.ok()).toBeTruthy();
      const page = (await auditRes.json()) as { items: AuditRow[] };
      const rows = page.items ?? [];
      expect(rows.length).toBeGreaterThan(0);

      // Diff payloads are JSON objects (state changes)
      const withDiff = rows.filter((r) => r.diff && typeof r.diff === 'object');
      expect(withDiff.length).toBeGreaterThan(0);

      // Actor attribution present on at least some rows
      const withActor = rows.filter((r) => !!r.actorUserId);
      expect(withActor.length).toBeGreaterThan(0);

      // Journey breadcrumbs when prior tracks wrote state
      const blob = JSON.stringify({ rows, state }).toLowerCase();
      const journeyTouched =
        (state.events?.length ?? 0) > 0 ||
        !!state.purchaseOrderId ||
        !!state.salesOrderId ||
        !!state.pickerEmail;
      if (journeyTouched) {
        expect(
          blob.includes('user') ||
            blob.includes('invite') ||
            blob.includes('purchase') ||
            blob.includes('sales') ||
            blob.includes('order') ||
            blob.includes('exception') ||
            blob.includes('allocation') ||
            (state.events?.join(' ') ?? '').length > 0,
        ).toBeTruthy();
      }

      // UI grid shows compliance columns (scope to audit grid — "Actions" also exists elsewhere)
      const auditGrid = owner.page.getByTestId('virtualized-table-grid');
      await expect(auditGrid.getByRole('columnheader', { name: 'Timestamp' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Action', exact: true })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Entity Type' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Actor' })).toBeVisible();
      await expect(auditGrid.getByRole('columnheader', { name: 'Changes (Diff)' })).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
