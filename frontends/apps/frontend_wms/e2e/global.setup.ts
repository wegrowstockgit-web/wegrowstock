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

interface SessionResponse {
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
}

function authPath(key: string): string {
  return path.join(AUTH_DIR, `${key}.json`);
}

/**
 * Programmatic login for each seeded demo role. Persists Playwright storage
 * state with HttpOnly session cookies + Zustand user profile (no JWTs in JS).
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
          `Auth setup failed for ${role.email}: HTTP ${loginResponse.status()} ${body}`,
        );
      }

      const session = (await loginResponse.json()) as SessionResponse;
      if ('accessToken' in (session as object) && (session as { accessToken?: string }).accessToken) {
        throw new Error(`Login leaked accessToken in JSON for ${role.email}`);
      }

      await page.goto('/login');
      await page.evaluate(
        ({ session: s, email, displayName }) => {
          localStorage.setItem(
            'invsys-session',
            JSON.stringify({
              state: {
                authenticated: true,
                user: {
                  id: s.userId,
                  email,
                  displayName,
                  roles: s.roles,
                  warehouseIds: s.warehouseIds ?? [],
                  tenantId: s.tenantId,
                },
                lastRequestId: null,
                primarySession: null,
              },
              version: 0,
            }),
          );
        },
        { session, email: role.email, displayName: role.displayName },
      );

      await context.storageState({ path: authPath(role.key) });
      await writeFile(
        path.join(AUTH_DIR, `${role.key}.meta.json`),
        JSON.stringify(
          {
            email: role.email,
            roles: session.roles,
            warehouseIds: session.warehouseIds ?? [],
            tenantId: session.tenantId,
            userId: session.userId,
          },
          null,
          2,
        ),
        'utf8',
      );
      await context.close();
    }
  } finally {
    await browser.close();
  }
}

export default globalSetup;
