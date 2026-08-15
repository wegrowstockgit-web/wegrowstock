import { defineConfig, devices } from '@playwright/test';

/**
 * Mobile scanner PIN lock suite — fresh picker login via contextForRole,
 * no globalSetup (avoids requiring every demo role including admin).
 */
export default defineConfig({
  testDir: '.',
  testMatch: ['tests/e2e/scanner-lock.spec.ts'],
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',
  timeout: 180_000,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'Mobile-Scanner',
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
  ],
});
