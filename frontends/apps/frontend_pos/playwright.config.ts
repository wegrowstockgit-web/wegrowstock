import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  timeout: 60_000,
  use: {
    baseURL: process.env.E2E_POS_URL ?? 'http://localhost:5175',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: process.env.E2E_POS_URL
    ? undefined
    : {
        command: 'pnpm dev --host 127.0.0.1 --port 5175',
        url: 'http://127.0.0.1:5175',
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
  projects: [
    {
      name: 'register-touch',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        hasTouch: true,
        locale: 'en-US',
      },
    },
    {
      name: 'register-phone-es',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 390, height: 844 },
        hasTouch: true,
        isMobile: true,
        locale: 'es-MX',
      },
    },
  ],
});
