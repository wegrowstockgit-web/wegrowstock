import { expect, test } from './fixtures/roleFixture';
import { completeScannerPin, dismissOnboardingTourIfPresent } from './fixtures/roleFixture';
import { contextForRole } from './journeys/helpers';

/**
 * Multi-role support RAG — picker / B2B / warehouse manager personas.
 */
test.describe('Support assistant personas', () => {
  test.setTimeout(120_000);

  test('PICKER gets scanner inbound guidance', async ({ browser }) => {
    const picker = await contextForRole(browser, 'picker');
    try {
      await picker.page.setViewportSize({ width: 360, height: 640 });
      await picker.page.goto('/fulfillment');
      await completeScannerPin(picker.page);
      await expect(picker.page.getByTestId('warehouse-floor-shell')).toBeVisible({ timeout: 30_000 });

      await picker.page.getByTestId('support-assistant-fab').click();
      await picker.page.getByTestId('support-assistant-input').fill('How do I process an inbound shipment?');
      await picker.page.getByTestId('support-assistant-send').click();

      const reply = picker.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/scan/i, { timeout: 30_000 });
      await expect(reply).toContainText(/putaway|bin/i);
      await expect(reply).not.toContainText(/click create purchase order/i);
    } finally {
      await picker.close();
    }
  });

  test('B2B customer stays in showroom sandbox', async ({ browser }) => {
    const b2b = await contextForRole(browser, 'b2b');
    try {
      await b2b.page.goto('/showroom/catalog');
      await expect(b2b.page.getByRole('heading', { name: /catalog/i })).toBeVisible({
        timeout: 30_000,
      });
      await dismissOnboardingTourIfPresent(b2b.page);

      await b2b.page.getByTestId('support-assistant-fab').click();
      await b2b.page.getByTestId('support-assistant-input').fill('How do I view my inventory allocations?');
      await b2b.page.getByTestId('support-assistant-send').click();

      const reply = b2b.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/showroom/i, { timeout: 30_000 });
      await expect(reply).toContainText(/not visible/i);
      await expect(reply).not.toContainText(/bin location/i);
      await expect(reply).not.toContainText(/inventory_ledger/i);
    } finally {
      await b2b.close();
    }
  });

  test('WAREHOUSE_MANAGER gets damage exception guidance', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/exceptions');
      await completeScannerPin(manager.page);
      await expect(manager.page.getByRole('heading', { name: /Action required|exception/i })).toBeVisible({
        timeout: 45_000,
      });

      await manager.page.getByTestId('support-assistant-fab').click();
      await manager.page
        .getByTestId('support-assistant-input')
        .fill('What should I do if an item is damaged on the floor?');
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/exception|adjust|cycle/i, { timeout: 30_000 });
    } finally {
      await manager.close();
    }
  });

  test('WAREHOUSE_MANAGER sees cycle-count action button', async ({ browser }) => {
    const manager = await contextForRole(browser, 'manager');
    try {
      await manager.page.goto('/cycle-counts');
      await completeScannerPin(manager.page);
      await dismissOnboardingTourIfPresent(manager.page);

      await manager.page.getByTestId('support-assistant-fab').click();
      await manager.page
        .getByTestId('support-assistant-input')
        .fill('Please generate a cycle count for zone Aisle-4');
      await manager.page.getByTestId('support-assistant-send').click();

      const reply = manager.page.getByTestId('support-assistant-reply').last();
      await expect(reply).toContainText(/cycle count/i, { timeout: 30_000 });

      const actionBtn = manager.page.getByTestId('support-action-button');
      await expect(actionBtn).toBeVisible({ timeout: 15_000 });
      await expect(actionBtn).toHaveAttribute('data-action', 'generateCycleCount');
      await expect(actionBtn).toContainText(/Aisle-4/i);
    } finally {
      await manager.close();
    }
  });
});
