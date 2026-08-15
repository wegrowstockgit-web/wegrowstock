import { defineConfig, devices } from '@playwright/test';

/**
 * Focused device PIN / connectivity suite — no global role auth cache
 * (avoids depending on every demo email when only owner/picker are required).
 */
export default defineConfig({
  testDir: '.',
  testMatch: ['e2e/device-pin-and-connectivity.spec.ts'],
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',
  timeout: 90_000,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
