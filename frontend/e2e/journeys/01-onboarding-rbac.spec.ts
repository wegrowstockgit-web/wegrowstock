import { test } from '@playwright/test';
import {
  contextForRole,
  DEMO_PASSWORD,
  expect,
  expectFulfillmentSurface,
  freshLogin,
} from './helpers';
import { resetJourneyState, writeJourneyState } from './journeyState';

/**
 * Track 1 — Admin invites a picker; isolated context accepts invite; RBAC walls Surface A.
 * Uses browser.newContext() for independent Admin / Picker sessions.
 */
test.describe.serial('Journey 01: Onboarding & RBAC boundary', () => {
  test('admin invites picker → accept → Surface B only', async ({ browser }) => {
    resetJourneyState();
    const pickerEmail = `journey.picker.${Date.now()}@demo.test`;

    const admin = await contextForRole(browser, 'admin');
    try {
      // Capture invite API (raw token + hash) while driving the Settings UI.
      const inviteWait = admin.page.waitForResponse(
        (res) =>
          res.url().includes('/api/v1/users/invitations') &&
          res.request().method() === 'POST' &&
          res.ok(),
      );

      await admin.page.goto('/settings/users');
      await expect(admin.page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible({
        timeout: 20_000,
      });
      await expect(admin.page.getByRole('button', { name: 'Invite user' })).toBeVisible();
      await admin.page.getByRole('button', { name: 'Invite user' }).click();
      await admin.page.getByLabel('Email').fill(pickerEmail);
      await admin.page.locator('#role').selectOption('PICKER');
      await admin.page.getByRole('button', { name: 'Send invitation' }).click();

      const inviteRes = await inviteWait;
      const inviteBody = (await inviteRes.json()) as {
        id: string;
        email: string;
        token: string;
        tokenHash: string;
      };
      expect(inviteBody.token).toBeTruthy();
      expect(inviteBody.tokenHash).toBeTruthy();
      expect(inviteBody.email).toBe(pickerEmail);

      writeJourneyState({
        pickerEmail,
        inviteTokenHash: inviteBody.tokenHash,
        events: [`INVITE_CREATED:${inviteBody.tokenHash}`],
      });

      // --- Isolated picker context (no shared cookies) ---
      const pickerCtx = await browser.newContext({
        baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
      });
      const pickerPage = await pickerCtx.newPage();
      try {
        // Prompt path alias: /invite/accept?token=… → real route /invite/:token
        await pickerPage.goto(`/invite/accept?token=${inviteBody.token}`);
        // If alias unsupported, fall through to canonical path
        if (!(await pickerPage.getByTestId('invite-accept-page').isVisible().catch(() => false))) {
          await pickerPage.goto(`/invite/${inviteBody.token}`);
        }
        await expect(pickerPage.getByTestId('invite-accept-page')).toBeVisible({ timeout: 15_000 });
        await pickerPage.getByLabel('First Name').fill('Journey');
        await pickerPage.getByLabel('Last Name').fill('Picker');
        await pickerPage.getByLabel('Password', { exact: true }).fill(DEMO_PASSWORD);
        await pickerPage.getByLabel('Confirm Password').fill(DEMO_PASSWORD);
        const acceptWait = pickerPage.waitForResponse(
          (res) => res.url().includes('/api/v1/invitations/accept') && res.request().method() === 'POST',
        );
        await pickerPage.getByRole('button', { name: 'Join team' }).click();
        const acceptRes = await acceptWait;
        expect(acceptRes.ok(), await acceptRes.text()).toBeTruthy();
        await expect(pickerPage).toHaveURL(/\/login/, { timeout: 20_000 });
      } finally {
        await pickerCtx.close();
      }

      // Fresh login as the new picker — must land on Surface B
      const pickerSession = await freshLogin(browser, pickerEmail, DEMO_PASSWORD);
      try {
        await expectFulfillmentSurface(pickerSession.page);

        // Force-navigate to Surface A billing — RBAC redirect + API 403
        await pickerSession.page.goto('/settings/billing');
        await expectFulfillmentSurface(pickerSession.page, 15_000);

        const billingStatus = await pickerSession.page.request.get('/api/v1/billing/stripe/status');
        expect([401, 403]).toContain(billingStatus.status());

        const me = await pickerSession.page.request.get('/api/v1/auth/me');
        if (me.ok()) {
          const profile = (await me.json()) as { id?: string; userId?: string };
          writeJourneyState({
            pickerUserId: profile.id ?? profile.userId,
            events: [`PICKER_REGISTERED:${pickerEmail}`],
          });
        } else {
          writeJourneyState({ events: [`PICKER_REGISTERED:${pickerEmail}`] });
        }
      } finally {
        await pickerSession.close();
      }
    } finally {
      await admin.close();
    }
  });
});
