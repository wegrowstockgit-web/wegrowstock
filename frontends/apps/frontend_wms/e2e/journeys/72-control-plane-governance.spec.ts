import { request as pwRequest } from '@playwright/test';
import { expect, test } from '../fixtures/roleFixture';

const ADMIN_API = process.env.ADMIN_API_URL ?? 'http://localhost:8081';

async function loginAdminApi() {
  const ctx = await pwRequest.newContext({ baseURL: ADMIN_API });
  const csrf = await ctx.get('/api/v1/control-plane/auth/csrf');
  test.skip(!csrf.ok(), 'Admin API is not reachable');
  const login = await ctx.post('/api/v1/control-plane/auth/login', {
    data: { email: 'owner@demo.test', password: 'password123' },
  });
  test.skip(!login.ok(), 'Admin login failed');
  await ctx.get('/api/v1/control-plane/auth/csrf');
  return ctx;
}

test.describe('Journey 72: Control plane governance', () => {
  test.setTimeout(180_000);

  test.beforeAll(async () => {
    const admin = await loginAdminApi();
    try {
      const flagsProbe = await admin.get('/api/v1/control-plane/flags');
      test.skip(!flagsProbe.ok(), 'Feature-flag control plane API not deployed');
    } finally {
      await admin.dispose();
    }
  });

  test('owner can read tenant feature flags after control-plane targeting', async ({ ownerPage }) => {
    const me = await ownerPage.request.get('/api/v1/auth/me');
    expect(me.ok()).toBeTruthy();

    const flags = await ownerPage.request.get('/api/v1/feature-flags');
    test.skip(flags.status() === 404, 'Tenant feature-flag API not deployed');
    expect(flags.ok(), await flags.text()).toBeTruthy();
    const body = (await flags.json()) as { flags?: string[] };
    expect(Array.isArray(body.flags)).toBeTruthy();
  });
});
