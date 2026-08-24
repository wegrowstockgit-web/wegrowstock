import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 85 — Warehouse settings list/map hybrid, search, peek drawer, bulk placeholder.
 */
test.describe('Journey 85: Warehouse list view, map toggle, and location drawer', () => {
  test.setTimeout(180_000);

  test('defaults to list, searches a bin, opens drawer, and toggles the map', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=warehouses');
      await expect(owner.page.getByTestId('warehouse-visualizer')).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.getByTestId('warehouse-view-list')).toHaveAttribute(
        'aria-pressed',
        'true',
      );
      await expect(owner.page.getByTestId('digital-twin-map')).toHaveCount(0);
      await expect(owner.page.getByTestId('warehouse-location-search')).toBeVisible();

      await owner.page.getByTestId('warehouse-location-search').fill('B-01');
      const binRow = owner.page.locator('[data-testid="warehouse-node-BIN"]').first();
      await expect(binRow).toBeVisible();
      await binRow.click();
      await expect(owner.page.getByTestId('right-peek-drawer')).toBeVisible();
      await expect(owner.page.getByTestId('warehouse-location-form')).toBeVisible();
      await expect(owner.page.getByTestId('location-edit-name')).toBeVisible();
      await expect(owner.page.getByTestId('location-edit-pick-sequence')).toBeVisible();
      await owner.page.getByTestId('location-edit-pick-sequence').fill('12');
      const saveWait = owner.page.waitForResponse(
        (res) =>
          /\/api\/v1\/locations\/[0-9a-f-]+$/.test(new URL(res.url()).pathname) &&
          res.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await owner.page.getByTestId('location-edit-save').click();
      expect((await saveWait).ok()).toBeTruthy();
      await expect(owner.page.getByTestId('right-peek-drawer')).toHaveCount(0);

      await owner.page.getByTestId('bulk-generate-bins').click();
      await expect(owner.page.getByTestId('bulk-generate-modal')).toBeVisible();
      await owner.page.getByRole('button', { name: 'Close dialog' }).click();
      await expect(owner.page.getByTestId('bulk-generate-modal')).toBeHidden();

      await owner.page.getByTestId('warehouse-view-map').click();
      await expect(owner.page.getByTestId('digital-twin-map')).toBeVisible();
      await expect(owner.page.getByTestId('digital-twin-svg')).toBeVisible();
      await owner.page.getByTestId('warehouse-view-list').click();
      await expect(owner.page.getByTestId('digital-twin-map')).toHaveCount(0);
    } finally {
      await owner.close();
    }
  });
});
