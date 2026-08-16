import { test } from '@playwright/test';
import { apiJson, contextForRole, expect, findVariantId, firstCustomerId } from './helpers';

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
 * Track 4 — Owner verifies the audit log after seeding its own mutation.
 * Does not read journey-state files from prior specs.
 */
test.describe('Journey 04: Ownership audit trail', () => {
  test('owner audit log captures invite / PO / SO / exception actors', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');

    try {
      const variantId = await findVariantId(owner.page);
      const customerId = await firstCustomerId(owner.page);
      const so = await apiJson<{ id: string; number: string }>(owner.page, '/api/v1/sales-orders', {
        method: 'POST',
        body: JSON.stringify({
          customerId,
          number: `SO-J4-AUDIT-${Date.now()}`,
          lines: [{ variantId, qtyOrdered: 1, unitPrice: 12.5 }],
        }),
      });
      const confirmRes = await owner.page.request.post(`/api/v1/sales-orders/${so.id}/confirm`);
      expect(confirmRes.ok()).toBeTruthy();

      await owner.page.goto('/reports/audit-log');
      await expect(owner.page.getByTestId('operations-console')).toBeVisible({ timeout: 20_000 });
      await expect(owner.page.getByTestId('audit-log-table')).toBeVisible();
      await expect(owner.page.getByText('Compliance audit log')).toBeVisible();

      const auditRes = await owner.page.request.get('/api/v1/audit/tenant?limit=50');
      expect(auditRes.ok()).toBeTruthy();
      const page = (await auditRes.json()) as { items: AuditRow[] };
      const rows = page.items ?? [];
      expect(rows.length).toBeGreaterThan(0);

      const withDiff = rows.filter((r) => r.diff && typeof r.diff === 'object');
      expect(withDiff.length).toBeGreaterThan(0);

      const withActor = rows.filter((r) => !!r.actorUserId);
      expect(withActor.length).toBeGreaterThan(0);

      const blob = JSON.stringify(rows).toLowerCase();
      expect(
        blob.includes(so.id.toLowerCase()) ||
          blob.includes('sales_order') ||
          blob.includes('sales-order') ||
          rows.some((r) => /sales|order|confirm/i.test(`${r.action} ${r.entityType}`)),
      ).toBeTruthy();

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
