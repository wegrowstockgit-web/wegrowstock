import { expect, test } from '../fixtures/roleFixture';
import { contextForRole, expectFulfillmentSurface } from './helpers';

/**
 * Journey 60 — Safari/Firefox-safe hardware degradation + iOS PWA meta tags.
 * Chromium Playwright has no Web Bluetooth / Web Serial, so the typed fallback
 * path is the live functional surface.
 */
test.describe('Journey 60: Cross-browser hardware fallback + iOS PWA', () => {
  test.setTimeout(180_000);

  test('PWA apple meta tags, no hardware connect buttons, typed weight/scan fallback', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      const pageErrors: string[] = [];
      owner.page.on('pageerror', (err) => pageErrors.push(err.message));

      await owner.page.goto('/');
      const appleCapable = owner.page.locator('meta[name="apple-mobile-web-app-capable"]');
      await expect(appleCapable).toHaveAttribute('content', 'yes');
      await expect(
        owner.page.locator('meta[name="apple-mobile-web-app-status-bar-style"]'),
      ).toHaveAttribute('content', 'black-translucent');
      await expect(owner.page.locator('meta[name="apple-mobile-web-app-title"]')).toHaveAttribute(
        'content',
        'weGrowStock',
      );
      await expect(owner.page.locator('link[rel="apple-touch-icon"]')).toHaveAttribute(
        'href',
        '/favicon.svg',
      );

      await owner.page.goto('/fulfillment');
      await expectFulfillmentSurface(owner.page);

      await expect(owner.page.getByRole('button', { name: /connect bluetooth scale/i })).toHaveCount(
        0,
      );
      await expect(owner.page.getByRole('button', { name: /connect usb scanner/i })).toHaveCount(0);

      const keyboard = owner.page.getByTestId('scanner-keyboard-entry').first();
      await expect(keyboard).toBeVisible();
      await keyboard.click();
      const scanFallback = owner.page.getByTestId('hardware-manual-fallback').filter({
        has: owner.page.getByLabel('Manual scan'),
      });
      await expect(scanFallback).toBeVisible();
      await scanFallback.getByLabel('Manual scan').fill('SKU-FALLBACK-1');
      await scanFallback.getByLabel('Manual scan').press('Enter');

      await owner.page.getByRole('button', { name: 'Pack', exact: true }).click();
      await expect(owner.page.getByLabel(/weight override/i)).toBeVisible({ timeout: 20_000 });
      await owner.page.getByLabel(/weight override/i).fill('2.50');
      await expect(
        owner.page
          .getByText(
            /Web Serial \/ Bluetooth unavailable|billable weight|packing scale|Connect packing scale/i,
          )
          .first(),
      ).toBeVisible();

      expect(pageErrors, pageErrors.join('\n')).toEqual([]);
    } finally {
      await owner.close();
    }
  });
});
