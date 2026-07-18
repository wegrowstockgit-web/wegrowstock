import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 32 — Settings split-pane scroll + sticky table heads + PO Document AI dropzone.
 */
test.describe('Journey 32: Settings scroll & Document AI upload', () => {
  test.setTimeout(180_000);

  test('settings nav stays put; content scrolls; sticky heads; PO dropzone', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/settings?tab=accounting');
      await expect(owner.page.getByTestId('settings-page')).toBeVisible({ timeout: 30_000 });
      await expect(owner.page.getByTestId('settings-nav')).toBeVisible();
      await expect(owner.page.getByTestId('settings-content')).toBeVisible();
      await expect(owner.page.getByTestId('accounting-sync')).toBeVisible({ timeout: 20_000 });

      const content = owner.page.getByTestId('settings-content');
      const canScroll = await content.evaluate((el) => el.scrollHeight > el.clientHeight + 40);
      expect(canScroll, 'settings content should own vertical overflow').toBeTruthy();

      // Native scrollbar chrome hidden (fade/chevron cues convey overflow instead).
      await expect
        .poll(async () =>
          content.evaluate((el) => {
            const style = getComputedStyle(el);
            return style.scrollbarWidth === 'none' || el.classList.contains('scrollbar-none');
          }),
        )
        .toBeTruthy();

      const navBoxBefore = await owner.page.getByTestId('settings-nav').boundingBox();
      expect(navBoxBefore).toBeTruthy();

      // Scroll the right panel (not the window) — nav Y should stay put on desktop.
      await content.evaluate((el) => {
        el.scrollTop = Math.min(el.scrollHeight - el.clientHeight, 420);
      });
      await expect
        .poll(async () => content.evaluate((el) => el.scrollTop))
        .toBeGreaterThan(40);

      const navBoxAfter = await owner.page.getByTestId('settings-nav').boundingBox();
      expect(navBoxAfter).toBeTruthy();
      expect(Math.abs((navBoxAfter!.y ?? 0) - (navBoxBefore!.y ?? 0))).toBeLessThan(2);

      // Sticky column headers pin inside the settings content scrollport.
      const head = content.locator('th').first();
      await expect(head).toBeVisible();
      await expect
        .poll(async () => head.evaluate((el) => getComputedStyle(el).position))
        .toBe('sticky');

      await owner.page.getByRole('button', { name: 'Operations' }).click();
      await expect(owner.page.getByTestId('operations-console')).toBeVisible({ timeout: 15_000 });
      await expect(content.locator('th').first()).toBeVisible();

      await owner.page.getByRole('button', { name: 'Sync Conflicts' }).click();
      await expect(owner.page.getByTestId('sync-conflicts-panel')).toBeVisible({ timeout: 15_000 });

      await owner.page.goto('/purchase-orders');
      await expect(owner.page.getByRole('heading', { name: 'Purchase Orders', exact: true })).toBeVisible({
        timeout: 30_000,
      });
      await expect(owner.page.getByTestId('document-ai-dropzone')).toBeVisible();
      await expect(owner.page.getByTestId('document-ai-upload-btn')).toBeVisible();
      await expect(owner.page.getByTestId('document-ai-dropzone')).toContainText(/Drop a PDF|browse/i);
    } finally {
      await owner.close();
    }
  });
});
