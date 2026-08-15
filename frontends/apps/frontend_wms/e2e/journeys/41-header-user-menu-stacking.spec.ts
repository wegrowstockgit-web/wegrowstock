import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 41 — Account menu (Profile / Sign out) must paint above dashboard CTAs.
 */
test.describe('Journey 41: Header user menu stacking', () => {
  test.setTimeout(120_000);

  test('sign out menu item is clickable above New sales order', async ({ browser }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1280, height: 800 });
      await owner.page.goto('/dashboard');
      await expect(owner.page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 45_000 });

      await owner.page.getByTestId('header-user-trigger').click();
      const menu = owner.page.getByTestId('header-user-menu');
      await expect(menu).toBeVisible();

      const signOut = menu.getByRole('menuitem', { name: 'Sign out' });
      await expect(signOut).toBeVisible();

      // Menu must sit above the primary dashboard CTA (same failure mode as the screenshot).
      const salesCta = owner.page.getByRole('button', { name: /New sales order/i });
      if (await salesCta.isVisible()) {
        const above = await owner.page.evaluate(() => {
          const menuEl = document.querySelector('[data-testid="header-user-menu"]');
          const cta = Array.from(document.querySelectorAll('button')).find((b) =>
            /New sales order/i.test(b.textContent ?? ''),
          );
          if (!menuEl || !cta) return false;
          const menuBox = menuEl.getBoundingClientRect();
          const ctaBox = cta.getBoundingClientRect();
          const x = Math.min(menuBox.right - 8, Math.max(menuBox.left + 8, ctaBox.left + 8));
          const y = Math.min(menuBox.bottom - 8, Math.max(menuBox.top + 8, ctaBox.top + 8));
          const top = document.elementFromPoint(x, y);
          return Boolean(top && (menuEl === top || menuEl.contains(top)));
        });
        expect(above, 'account menu must be the topmost hit target over dashboard CTAs').toBeTruthy();
      }

      await expect(signOut).toBeEnabled();
    } finally {
      await owner.close();
    }
  });
});
