import { expect, test } from '../../e2e/fixtures/roleFixture';
import { contextForRole } from '../../e2e/journeys/helpers';

/**
 * Tablet-Manager / B2B sandbox — exclusive B2B_CUSTOMER on ShowroomLayout.
 * Internal office routes must always bounce back to /showroom/catalog.
 */
test.describe('B2B showroom sandbox suite', () => {
  test.setTimeout(120_000);

  test('showroom shell only; office routes rejected to catalog', async ({ browser }, testInfo) => {
    expect(testInfo.project.name).toBe('Tablet-Manager');
    const viewport = testInfo.project.use.viewport;
    expect(viewport?.width).toBe(1024);
    expect(viewport?.height).toBe(768);
    expect(testInfo.project.use.hasTouch).toBe(true);

    const b2b = await contextForRole(browser, 'b2b');
    try {
      await b2b.page.goto('/showroom/catalog');
      await expect(b2b.page).toHaveURL(/\/showroom\/catalog/, { timeout: 20_000 });
      await expect(b2b.page.getByTestId('showroom-layout')).toBeVisible({ timeout: 15_000 });
      await expect(b2b.page.getByRole('heading', { name: 'Catalog' })).toBeVisible({
        timeout: 15_000,
      });

      // Strictly showroom — never the internal AppShell / office rail.
      await expect(b2b.page.getByTestId('app-shell')).toHaveCount(0);
      await expect(b2b.page.getByTestId('icon-rail')).toHaveCount(0);
      await expect(b2b.page.getByText('Wholesale Portal')).toBeVisible();

      for (const path of ['/dashboard', '/fulfillment', '/inventory'] as const) {
        await b2b.page.goto(path);
        await expect(b2b.page).toHaveURL(/\/showroom\/catalog/, { timeout: 15_000 });
        await expect(b2b.page.getByTestId('showroom-layout')).toBeVisible();
        await expect(b2b.page.getByTestId('app-shell')).toHaveCount(0);
      }
    } finally {
      await b2b.close();
    }
  });
});
