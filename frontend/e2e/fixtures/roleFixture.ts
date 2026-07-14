import path from 'node:path';
import { test as base, type Page, type Browser } from '@playwright/test';

const AUTH_DIR = path.join(process.cwd(), 'playwright', '.auth');

function storageStateFor(role: string): string {
  return path.join(AUTH_DIR, `${role}.json`);
}

async function pageForRole(browser: Browser, role: string): Promise<{ page: Page; close: () => Promise<void> }> {
  const context = await browser.newContext({
    storageState: storageStateFor(role),
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
  });
  const page = await context.newPage();
  return {
    page,
    close: async () => {
      await context.close();
    },
  };
}

type RoleFixtures = {
  ownerPage: Page;
  adminPage: Page;
  managerPage: Page;
  pickerPage: Page;
  viewerPage: Page;
  b2bPage: Page;
};

/**
 * Role-authenticated page fixtures backed by cached storage state from global setup.
 */
export const test = base.extend<RoleFixtures>({
  ownerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'owner');
    await use(page);
    await close();
  },
  adminPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'admin');
    await use(page);
    await close();
  },
  managerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'manager');
    await use(page);
    await close();
  },
  pickerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'picker');
    await use(page);
    await close();
  },
  viewerPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'viewer');
    await use(page);
    await close();
  },
  b2bPage: async ({ browser }, use) => {
    const { page, close } = await pageForRole(browser, 'b2b');
    await use(page);
    await close();
  },
});

export { expect } from '@playwright/test';

/** Simulate a HID wedge barcode scan (fast keydown burst + Enter). */
export async function hidScan(page: Page, barcode: string): Promise<void> {
  await page.evaluate((code) => {
    for (const key of code) {
      document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    }
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  }, barcode);
}

export async function sessionAccessToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => {
    const raw = localStorage.getItem('invsys-session');
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { state?: { accessToken?: string } };
    return parsed.state?.accessToken ?? null;
  });
  if (!token) {
    throw new Error('No access token in invsys-session storage');
  }
  return token;
}
