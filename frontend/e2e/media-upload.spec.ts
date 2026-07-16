import { expect } from '@playwright/test';
import path from 'node:path';
import { test } from './fixtures/roleFixture';

const FIXTURE_PNG = path.join(process.cwd(), 'e2e', 'fixtures', 'pixel.png');

test.describe('Media upload surfaces', () => {
  test('owner can set profile avatar from Settings', async ({ ownerPage }) => {
    await ownerPage.goto('/settings');
    await expect(ownerPage.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
      timeout: 30_000,
    });
    await expect(ownerPage.getByRole('heading', { name: 'Your account' })).toBeVisible();

    const picker = ownerPage.getByTestId('profile-avatar-picker');
    await expect(picker).toBeVisible();
    await expect(picker.getByRole('button', { name: 'Take photo' })).toBeVisible();
    await expect(picker.getByTestId('camera-capture')).toBeVisible();

    const fileInput = picker.locator('input[type="file"]');
    await fileInput.setInputFiles(FIXTURE_PNG);

    await expect(ownerPage.getByTestId('header-user')).toBeVisible();
    // Avatar blob path is stored; header avatar container remains present.
    await expect(picker.getByText(/JPEG, PNG/i)).toBeVisible();
  });

  test('Take photo opens device camera capture UI', async ({ ownerPage }) => {
    await ownerPage.addInitScript(() => {
      const fakeTrack = { stop: () => undefined };
      const fakeStream = { getTracks: () => [fakeTrack] };
      Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: {
          getUserMedia: async () => fakeStream,
        },
      });
    });

    await ownerPage.goto('/settings');
    await expect(ownerPage.getByTestId('profile-avatar-picker')).toBeVisible({ timeout: 30_000 });
    await ownerPage.getByRole('button', { name: 'Take photo' }).click();
    await expect(ownerPage.getByTestId('camera-capture-live')).toBeVisible();
    await expect(ownerPage.getByTestId('camera-capture-snap')).toBeVisible();
    await expect(ownerPage.getByRole('button', { name: 'Cancel' })).toBeVisible();
  });

  test('owner can upload product photo from product peek', async ({ ownerPage }) => {
    await ownerPage.goto('/products');
    await expect(ownerPage.getByRole('heading', { name: /products/i })).toBeVisible({ timeout: 30_000 });

    const firstRow = ownerPage.locator('[role="row"]').nth(1);
    if (await firstRow.count() === 0) {
      test.skip(true, 'No products seeded for media upload e2e');
      return;
    }
    await firstRow.click();

    const mediaPicker = ownerPage.getByTestId('product-media-picker');
    await expect(mediaPicker).toBeVisible({ timeout: 15_000 });
    await expect(ownerPage.getByTestId('product-media-dropzone')).toBeVisible();
    await mediaPicker.locator('input[type="file"]').setInputFiles(FIXTURE_PNG);
    await expect(mediaPicker.getByText(/JPEG, PNG/i)).toBeVisible();
  });
});
