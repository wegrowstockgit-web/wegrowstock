import { dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 86 — Mobile off-canvas nav: opaque drawer, full-width workspace, auto-close.
 */
test.describe('Journey 86: Mobile sidebar drawer and opaque rail', () => {
  test.setTimeout(180_000);

  test('hamburger opens a solid drawer that closes after navigation', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 390, height: 844 });
      await owner.page.goto('/products');
      await expect(owner.page.getByTestId('app-shell')).toBeVisible({ timeout: 30_000 });
      await dismissOnboardingTourIfPresent(owner.page);

      const hamburger = owner.page.getByTestId('mobile-nav-toggle');
      await expect(hamburger).toBeVisible();
      await expect(owner.page.getByTestId('icon-rail')).toHaveAttribute('data-mobile-open', 'false');

      const mainBox = await owner.page.getByTestId('app-shell-main').boundingBox();
      expect(mainBox).toBeTruthy();
      expect(mainBox!.x).toBeLessThan(8);
      expect(mainBox!.width).toBeGreaterThan(370);

      await hamburger.click();
      await expect(owner.page.getByTestId('icon-rail')).toHaveAttribute('data-mobile-open', 'true');
      await expect(owner.page.getByTestId('mobile-nav-backdrop')).toBeVisible();
      await expect(owner.page.getByTestId('icon-rail')).toBeInViewport();

      const bg = await owner.page.getByTestId('icon-rail-panel').evaluate((el) => {
        const style = getComputedStyle(el);
        return { color: style.backgroundColor, filter: style.backdropFilter };
      });
      expect(bg.filter === 'none' || bg.filter === '').toBeTruthy();
      const alphaMatch = bg.color.match(/rgba?\(([^)]+)\)/);
      if (alphaMatch) {
        const parts = alphaMatch[1].split(',').map((part) => part.trim());
        if (parts.length === 4) {
          expect(Number.parseFloat(parts[3])).toBeGreaterThanOrEqual(1);
        }
      }
      expect(bg.color).not.toBe('transparent');

      await owner.page.getByTestId('nav-dashboard').click();
      await expect(owner.page).toHaveURL(/\/dashboard/);
      await expect(owner.page.getByTestId('icon-rail')).toHaveAttribute('data-mobile-open', 'false');
      await expect(owner.page.getByTestId('mobile-nav-backdrop')).toHaveCount(0);
    } finally {
      await owner.close();
    }
  });
});
