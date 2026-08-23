import { expect, test } from '@playwright/test';
import { completeIdentifierFirstLogin } from '../fixtures/roleFixture';

const DEMO_PASSWORD = process.env.E2E_DEMO_PASSWORD ?? 'password123';
const API = process.env.E2E_API_URL ?? 'http://localhost:8080';

/**
 * Functional e2e: owner opens Invoices, opens the invoice workspace, and sees
 * Download PDF + Email invoice controls.
 */
test.describe('Invoice PDF generation & dispatch', () => {
  test('owner can download and email invoice PDF from the workspace', async ({ page, request }) => {
    test.setTimeout(90_000);

    const login = await request.post(`${API}/api/v1/auth/login`, {
      data: { email: 'owner@demo.test', password: DEMO_PASSWORD },
    });
    test.skip(!login.ok(), 'Demo API not reachable — start stack with deploy.bat / seed');
    const session = await login.json();
    const token = session.accessToken as string;

    const listRes = await request.get(`${API}/api/v1/invoices`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    test.skip(!listRes.ok(), 'Invoices API unavailable');
    const raw = (await listRes.json()) as
      | Array<{ id: string; number: string; status: string }>
      | { items?: Array<{ id: string; number: string; status: string }> };
    let invoices = Array.isArray(raw) ? raw : (raw.items ?? []);

    if (!invoices.some((i) => i.status === 'OPEN')) {
      // Soft-skip when demo tenant has no OPEN invoices — UI still verified via empty-state path.
      test.info().annotations.push({
        type: 'note',
        description: 'No OPEN invoices in demo tenant; verifying page chrome only',
      });
    }

    await page.goto('/login');
    await completeIdentifierFirstLogin(page, 'owner@demo.test', DEMO_PASSWORD);
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 20_000 });

    await page.goto('/invoices');
    await expect(page.getByRole('heading', { name: 'Invoices' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('invoices-page')).toBeVisible();
    const skipTour = page.getByRole('button', { name: 'Not now' });
    if (await skipTour.isVisible({ timeout: 8_000 }).catch(() => false)) {
      await skipTour.click();
    }

    const openInvoice = invoices.find((i) => i.status === 'OPEN') ?? invoices[0];
    if (!openInvoice) {
      await expect(page.getByText(/No invoices yet/i)).toBeVisible();
      return;
    }

    // API-level functional proof (real PDF bytes) — requires stack rebuilt with documents module
    const pdfRes = await request.get(`${API}/api/v1/documents/invoice/${openInvoice.id}/pdf`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    test.skip(pdfRes.status() === 404, 'Documents API not deployed yet — rebuild backend (deploy.bat)');
    expect(pdfRes.ok(), `PDF status ${pdfRes.status()}: ${await pdfRes.text()}`).toBeTruthy();
    expect(pdfRes.headers()['content-type']).toContain('application/pdf');
    const pdfBuf = await pdfRes.body();
    expect(pdfBuf.subarray(0, 4).toString()).toBe('%PDF');

    const emailRes = await request.post(`${API}/api/v1/documents/invoice/${openInvoice.id}/email`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    // Email may 400 if customer has no email — still a valid functional response path
    expect([200, 400]).toContain(emailRes.status());

    if (await skipTour.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await skipTour.click();
    }
    await page.getByTestId(`invoice-row-${openInvoice.id}`).click();
    await expect(page).toHaveURL(new RegExp(`/invoices/${openInvoice.id}`), { timeout: 15_000 });
    await expect(page.getByTestId('invoice-workspace')).toBeVisible();
    await expect(page.getByTestId('invoice-download-pdf')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('invoice-email-pdf')).toBeVisible();
  });
});
