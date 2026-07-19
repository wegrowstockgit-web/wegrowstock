import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = path.dirname(fileURLToPath(import.meta.url));

/**
 * Persona-driven projects:
 * - Desktop-Admin — office chrome (1920×1080 Chrome/Edge)
 * - Mobile-Scanner — Zebra/Honeywell rugged Android (360×640, touch)
 * - Tablet-Manager — iPad floor / B2B tablet (1024×768, touch)
 * - chromium — legacy journey suite (Desktop Chrome)
 */
export default defineConfig({
  testDir: '.',
  testMatch: ['e2e/**/*.spec.ts', 'tests/e2e/**/*.spec.ts'],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'list',
  timeout: 90_000,
  globalSetup: path.join(rootDir, 'e2e', 'global.setup.ts'),
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'Desktop-Admin',
      testMatch: ['**/tests/e2e/admin.spec.ts'],
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1920, height: 1080 },
        // Prefer installed Edge when requested: E2E_DESKTOP_CHANNEL=msedge
        channel: process.env.E2E_DESKTOP_CHANNEL === 'msedge' ? 'msedge' : undefined,
      },
    },
    {
      name: 'Mobile-Scanner',
      testMatch: [
        '**/tests/e2e/picker.spec.ts',
        '**/tests/e2e/offline.spec.ts',
        '**/tests/e2e/scanner-lock.spec.ts',
      ],
      use: {
        ...devices['Pixel 5'],
        browserName: 'chromium',
        viewport: { width: 360, height: 640 },
        deviceScaleFactor: 2,
        isMobile: true,
        hasTouch: true,
        userAgent:
          'Mozilla/5.0 (Linux; Android 13; TC57) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
      },
    },
    {
      name: 'Tablet-Manager',
      testMatch: ['**/tests/e2e/b2b-showroom.spec.ts'],
      use: {
        ...devices['iPad Mini'],
        browserName: 'chromium',
        viewport: { width: 1024, height: 768 },
        deviceScaleFactor: 2,
        isMobile: false,
        hasTouch: true,
        userAgent:
          'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
      },
    },
    {
      name: 'chromium',
      testMatch: ['**/e2e/**/*.spec.ts'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
