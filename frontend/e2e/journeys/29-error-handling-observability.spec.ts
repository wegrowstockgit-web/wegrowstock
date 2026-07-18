import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 29 — Launch readiness: 404 page, route ErrorBoundary, list error UX.
 */
test.describe('Journey 29: Error handling & observability', () => {
  test.setTimeout(180_000);

  test('unknown path shows NotFound and returns to dashboard', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/this-route-does-not-exist-e2e-29');
      await expect(owner.page.getByTestId('not-found-page')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByText('Page not found')).toBeVisible();
      // Shell should still be present (sidebar / rail) — crash isolation for layout.
      await expect(owner.page.getByTestId('icon-rail')).toBeVisible();

      await owner.page.getByTestId('not-found-home').click();
      await expect(owner.page).toHaveURL(/\/dashboard/, { timeout: 20_000 });
    } finally {
      await owner.close();
    }
  });

  test('render crash is contained by ErrorBoundary with Retry', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/__e2e/crash');
      await expect(owner.page.getByTestId('error-boundary')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByText('Something went wrong')).toBeVisible();
      await expect(owner.page.getByTestId('error-boundary-retry')).toBeVisible();
      // App chrome survives the crashed outlet.
      await expect(owner.page.getByTestId('icon-rail')).toBeVisible();
    } finally {
      await owner.close();
    }
  });

  test('list pages surface error state when API fails', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.route('**/api/v1/customers**', (route) =>
        route.fulfill({
          status: 500,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            title: 'Internal Server Error',
            detail: 'Synthetic failure for e2e',
            status: 500,
          }),
        }),
      );

      await owner.page.goto('/customers');
      await expect(owner.page.getByTestId('list-page-error')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByTestId('list-page-retry')).toBeVisible();
    } finally {
      await owner.close();
    }
  });
});
