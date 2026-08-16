import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = path.dirname(fileURLToPath(import.meta.url));

/**
 * Control-plane E2E against the live Docker stack.
 * No WMS globalSetup — these specs log into admin.invsys.com themselves.
 */
export default defineConfig({
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'list',
  timeout: 180_000,
  use: {
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'journeys',
      testDir: rootDir,
      testMatch: ['journeys/61-*.spec.ts', 'journeys/62-*.spec.ts', 'journeys/63-*.spec.ts'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
