import { expect, test } from '../fixtures/roleFixture';
import { completeScannerPin, dismissOnboardingTourIfPresent } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/** 1×1 PNG — reliably decoded by compressImageForUpload / createImageBitmap. */
const TINY_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

/**
 * Step 5 functional E2E: multimodal camera attach + training flight simulator
 * Action Draft interception (no live ledger writes).
 */
test.describe('Support camera multimodal & training simulator', () => {
  test.setTimeout(180_000);

  test('picker can attach a photo and see thumbnail before send', async ({ browser }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 390, height: 844 });
      await picker.page.goto('/inbound/receive');
      await completeScannerPin(picker.page);
      await dismissOnboardingTourIfPresent(picker.page);

      await picker.page.getByTestId('support-assistant-fab').click();
      await expect(picker.page.getByTestId('support-assistant-panel')).toBeVisible();
      await expect(picker.page.getByTestId('support-camera-button')).toBeVisible();

      // Set the hidden file input directly (more reliable than OS filechooser in CI).
      await picker.page.getByTestId('support-camera-input').setInputFiles({
        name: 'torn-label.png',
        mimeType: 'image/png',
        buffer: TINY_PNG,
      });

      await expect(picker.page.getByTestId('support-image-pending')).toBeVisible({ timeout: 15_000 });
      await expect(picker.page.getByTestId('support-image-thumbnail')).toBeVisible();
      await expect(picker.page.getByTestId('support-image-pending')).toContainText(/torn-label/i);

      await picker.page
        .getByTestId('support-assistant-input')
        .fill('This barcode is torn — what should I do next?');
      await picker.page.getByTestId('support-assistant-send').click();

      const reply = picker.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/photo|label|barcode|Diagnosis|Action|Lots|Skip|torn/i, {
        timeout: 45_000,
      });
      await expect(reply).not.toContainText(/\/api\/v1|CQRS|SupportChatService/i);
    } finally {
      await picker.close();
    }
  });

  test('manager training simulator intercepts Action Draft without live writes', async ({
    browser,
  }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/cycle-counts');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await expect(manager.page.getByTestId('support-training-MANAGER_ALLOCATION')).toBeVisible();
      await manager.page.getByTestId('support-training-MANAGER_ALLOCATION').click();

      await expect(manager.page.getByTestId('support-training-simulator-header')).toBeVisible();
      await expect(manager.page.getByTestId('support-training-simulator-header')).toContainText(
        /TRAINING SIMULATOR ACTIVE/i,
      );
      await expect(manager.page.getByTestId('training-mission-banner')).toBeVisible();
      await expect(manager.page.getByTestId('training-mission-banner')).toContainText(
        /NO DATA WILL BE SAVED|no live stock/i,
      );

      await manager.page
        .getByTestId('support-assistant-input')
        .fill('Generate cycle count for zone Aisle-4 — do it for me');
      await manager.page.getByTestId('support-assistant-send').click();

      const draft = manager.page.getByTestId('support-action-draft');
      await expect(draft).toBeVisible({ timeout: 25_000 });

      // Intercept live draft-execute — simulator must not call it.
      let draftExecuteCalls = 0;
      await manager.page.route('**/api/v1/support/actions/draft-execute', async (route) => {
        draftExecuteCalls += 1;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ok: true }),
        });
      });
      await manager.page.route('**/api/v1/cycle-counts', async (route) => {
        if (route.request().method() === 'POST') {
          draftExecuteCalls += 1;
        }
        await route.continue();
      });

      await manager.page.getByTestId('support-draft-approve').click();
      await expect(manager.page.getByText(/Training scenario completed successfully/i)).toBeVisible({
        timeout: 15_000,
      });
      expect(draftExecuteCalls).toBe(0);

      await manager.page.getByTestId('training-mission-exit').click();
      await expect(manager.page.getByTestId('training-mission-banner')).toHaveCount(0);
    } finally {
      await manager.close();
    }
  });
});
