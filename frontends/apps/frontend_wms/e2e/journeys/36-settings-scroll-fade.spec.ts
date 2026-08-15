import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

async function expectNativeScrollbarHidden(locator: {
  evaluate: (fn: (el: HTMLElement) => boolean) => Promise<boolean>;
}) {
  const hidden = await locator.evaluate((el) => {
    const style = getComputedStyle(el);
    if (style.scrollbarWidth === 'none') return true;
    // WebKit/Blink: scrollbar-none removes the gutter even when overflowing.
    const overflows = el.scrollHeight > el.clientHeight + 8;
    const gutter = el.offsetWidth - el.clientWidth;
    return overflows && gutter === 0;
  });
  expect(hidden, 'native scrollbar chrome should be hidden').toBeTruthy();
}

/**
 * Journey 36 — Settings scrollports hide native scrollbars and show fold cues
 * (fade + chevrons), matching the primary sidebar affordance.
 */
test.describe('Journey 36: Settings scroll fade cues', () => {
  test.setTimeout(180_000);

  test('settings nav/content + sub-routes: hidden bars, scrollable, fold hints', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.setViewportSize({ width: 1440, height: 720 });
      await owner.page.goto('/settings?tab=inventory');
      await expect(owner.page.getByTestId('settings-page')).toBeVisible({ timeout: 30_000 });

      const nav = owner.page.getByTestId('settings-nav');
      const content = owner.page.getByTestId('settings-content');
      await expect(nav).toBeVisible();
      await expect(content).toBeVisible();

      // Content overflows on a short viewport with Inventory Rules.
      await expect
        .poll(async () => content.evaluate((el) => el.scrollHeight > el.clientHeight + 40))
        .toBeTruthy();

      await expectNativeScrollbarHidden(content);
      await expectNativeScrollbarHidden(nav);

      // More-below cue like the icon rail.
      await expect(owner.page.getByTestId('settings-content-scroll-down')).toBeVisible({
        timeout: 10_000,
      });

      // Nav list is long enough to overflow on this viewport.
      const navOverflows = await nav.evaluate((el) => el.scrollHeight > el.clientHeight + 8);
      if (navOverflows) {
        await expect(owner.page.getByTestId('settings-nav-scroll-down')).toBeVisible();
        await nav.evaluate((el) => {
          el.scrollTop = Math.min(el.scrollHeight - el.clientHeight, 240);
        });
        await expect(owner.page.getByTestId('settings-nav-scroll-up')).toBeVisible({
          timeout: 5_000,
        });
      }

      // Scroll content to bottom → up cue; down cue clears.
      await content.evaluate((el) => {
        el.scrollTop = el.scrollHeight;
      });
      await expect(owner.page.getByTestId('settings-content-scroll-up')).toBeVisible({
        timeout: 5_000,
      });
      await expect(owner.page.getByTestId('settings-content-scroll-down')).toHaveCount(0);

      // Sub-route: personal profile uses the same scroll-fade shell.
      await owner.page.goto('/settings/profile');
      await expect(owner.page.getByTestId('profile-settings-page')).toBeVisible({ timeout: 20_000 });
      const profileScroll = owner.page.getByTestId('profile-settings-page-scroll');
      await expect(profileScroll).toBeVisible();
      await expect
        .poll(async () => profileScroll.evaluate((el) => el.scrollHeight > el.clientHeight + 20))
        .toBeTruthy();
      await expectNativeScrollbarHidden(profileScroll);
      await expect(owner.page.getByTestId('profile-settings-page-scroll-scroll-down')).toBeVisible({
        timeout: 10_000,
      });

      // Billing sub-route
      await owner.page.goto('/settings/billing');
      await expect(owner.page.getByTestId('billing-settings-page')).toBeVisible({ timeout: 20_000 });
      const billingScroll = owner.page.getByTestId('billing-settings-page-scroll');
      await expect(billingScroll).toBeVisible();
      await expectNativeScrollbarHidden(billingScroll);
    } finally {
      await owner.close();
    }
  });
});
