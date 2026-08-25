import { completeScannerPin, dismissOnboardingTourIfPresent, expect, test } from '../fixtures/roleFixture';
import { contextForRole, expectFulfillmentSurface } from './helpers';

const UNSUPPORTED_BROWSER_HARDWARE_MESSAGE =
  'Your current browser does not support direct hardware connections. To connect USB or Bluetooth scales, please use Google Chrome or Microsoft Edge on a Windows, Mac, or Android device.';

/**
 * Journey 88 — Hardware Device Manager plug-and-play pairing UX on the floor.
 * Playwright Chromium does not expose Web Serial / Web Bluetooth, so Connect
 * for scales is hidden and the Chrome/Edge warning is the live path. QZ Tray
 * and HID scanners stay available. Pairing success is covered by unit tests.
 */
test.describe('Journey 88: Hardware Device Manager', () => {
  test.setTimeout(180_000);

  test('floor header opens Device Manager with scale warning and QZ connect', async ({
    browser,
  }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 360, height: 640 });
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({ timeout: 30_000 });
      await dismissOnboardingTourIfPresent(picker.page);
      await expectFulfillmentSurface(picker.page);

      await expect(picker.page.getByTestId('hardware-device-manager-open')).toBeVisible();
      await picker.page.getByTestId('hardware-device-manager-open').click();

      const modal = picker.page.getByTestId('device-manager-modal');
      await expect(modal).toBeVisible({ timeout: 10_000 });
      await expect(modal.getByRole('heading', { name: 'Hardware devices' })).toBeVisible();
      await expect(picker.page.getByTestId('device-row-bluetooth-scale')).toBeVisible();
      await expect(picker.page.getByTestId('device-row-serial-scale')).toBeVisible();
      await expect(picker.page.getByTestId('device-row-qz-tray')).toBeVisible();
      await expect(picker.page.getByTestId('device-row-bluetooth-scale')).toHaveAttribute(
        'data-connected',
        'false',
      );
      await expect(picker.page.getByTestId('device-row-serial-scale')).toHaveAttribute(
        'data-connected',
        'false',
      );

      const unsupported = picker.page.getByTestId('device-manager-unsupported-alert');
      if ((await unsupported.count()) > 0) {
        await expect(unsupported).toHaveText(UNSUPPORTED_BROWSER_HARDWARE_MESSAGE);
        await expect(picker.page.getByTestId('device-connect-bluetooth-scale')).toHaveCount(0);
        await expect(picker.page.getByTestId('device-connect-serial-scale')).toHaveCount(0);
      } else {
        await expect(picker.page.getByTestId('device-connect-bluetooth-scale')).toBeVisible();
        await expect(picker.page.getByTestId('device-connect-serial-scale')).toBeVisible();
      }
      await expect(picker.page.getByTestId('device-connect-qz-tray')).toBeVisible();
      await expect(picker.page.getByText(/HID barcode scanners are always available/i)).toBeVisible();

      await picker.page.getByRole('button', { name: 'Close dialog' }).click();
      await expect(modal).toBeHidden();

      await picker.page.getByRole('button', { name: 'Pack', exact: true }).click();
      await expect(picker.page.getByTestId('pack-weight-input')).toBeVisible({ timeout: 20_000 });
      await expect(picker.page.getByTestId('pack-weight-input')).toBeEditable();
      await expect(picker.page.getByTestId('packing-scale-connect')).toBeVisible();
      await picker.page.getByTestId('packing-scale-connect').click();
      await expect(picker.page.getByTestId('device-manager-modal')).toBeVisible();
    } finally {
      await picker.close();
    }
  });
});
