import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { chromium, type FullConfig } from '@playwright/test';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
const AUTH_DIR = path.join(process.cwd(), 'playwright', '.auth');

export const DEMO_ROLES = [
  { key: 'owner', email: 'owner@demo.test', displayName: 'Demo Owner' },
  { key: 'admin', email: 'admin@demo.test', displayName: 'Demo Admin' },
  { key: 'manager', email: 'manager@demo.test', displayName: 'Warehouse Manager' },
  { key: 'picker', email: 'picker@demo.test', displayName: 'Floor Picker' },
  { key: 'viewer', email: 'viewer@demo.test', displayName: 'Read Only User' },
  { key: 'b2b', email: 'b2b@demo.test', displayName: 'B2B Buyer' },
] as const;

export type DemoRoleKey = (typeof DEMO_ROLES)[number]['key'];

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tenantId: string;
  userId: string;
  roles: string[];
}

function authPath(key: string): string {
  return path.join(AUTH_DIR, `${key}.json`);
}

/**
 * Programmatic login for each seeded demo role. Persists Playwright storage
 * state with Zustand `invsys-session` hydrated so fixtures start authenticated.
 */
async function globalSetup(config: FullConfig): Promise<void> {
  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:3000';
  await mkdir(AUTH_DIR, { recursive: true });

  const browser = await chromium.launch();
  try {
    for (const role of DEMO_ROLES) {
      const context = await browser.newContext({ baseURL });
      const page = await context.newPage();

      const loginResponse = await page.request.post('/api/v1/auth/login', {
        data: {
          email: role.email,
          password: DEMO_PASSWORD,
        },
      });

      if (!loginResponse.ok()) {
        const body = await loginResponse.text();
        throw new Error(
          `Auth setup failed for ${role.email}: HTTP ${loginResponse.status()} ${body}`
        );
      }

      const tokens = (await loginResponse.json()) as TokenResponse;

      await page.goto('/login');
      await page.evaluate(
        ({ tokens: t, email, displayName }) => {
          localStorage.setItem(
            'invsys-session',
            JSON.stringify({
              state: {
                accessToken: t.accessToken,
                refreshToken: t.refreshToken,
                user: {
                  id: t.userId,
                  email,
                  displayName,
                  roles: t.roles,
                },
                lastRequestId: null,
              },
              version: 0,
            })
          );
        },
        { tokens, email: role.email, displayName: role.displayName }
      );

      await context.storageState({ path: authPath(role.key) });
      await writeFile(
        path.join(AUTH_DIR, `${role.key}.meta.json`),
        JSON.stringify(
          {
            email: role.email,
            roles: tokens.roles,
            tenantId: tokens.tenantId,
            userId: tokens.userId,
          },
          null,
          2
        ),
        'utf8'
      );
      await context.close();
    }
  } finally {
    await browser.close();
  }
}

export default globalSetup;
