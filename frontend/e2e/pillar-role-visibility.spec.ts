import { expect, test } from './fixtures/roleFixture';

test.describe('Pillar role visibility', () => {
  test('owner sees Compliance, Issue Supplies, and Settings cost centers tab', async ({
    ownerPage,
  }) => {
    await ownerPage.goto('/dashboard');
    await expect(ownerPage.getByTestId('icon-rail').getByRole('link', { name: 'Lot Trace' })).toBeVisible();
    await expect(ownerPage.getByTestId('icon-rail').getByRole('link', { name: 'Issue Supplies' })).toBeVisible();

    await ownerPage.goto('/settings');
    await expect(ownerPage.getByRole('button', { name: 'Cost Centers & Requisitions' })).toBeVisible();
  });

  test('viewer sees Compliance but not Issue Supplies nav or Issue Fact', async ({ viewerPage }) => {
    await viewerPage.goto('/dashboard');
    await expect(viewerPage.getByTestId('icon-rail').getByRole('link', { name: 'Lot Trace' })).toBeVisible();
    await expect(viewerPage.getByTestId('icon-rail').getByRole('link', { name: 'Issue Supplies' })).toHaveCount(0);

    await viewerPage.goto('/issue-supplies');
    await expect(viewerPage).not.toHaveURL(/\/issue-supplies/);
    await expect(viewerPage.getByRole('button', { name: 'Issue Fact' })).toHaveCount(0);
  });

  test('picker sees Issue Supplies and floor truck route; redirected from settings', async ({
    pickerPage,
  }) => {
    await pickerPage.goto('/fulfillment');
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();

    await pickerPage.goto('/issue-supplies');
    await expect(pickerPage).toHaveURL(/\/issue-supplies/);
    await expect(pickerPage.getByText('Floor ops')).toBeVisible();
    await expect(pickerPage.getByRole('heading', { name: 'Issue Supplies' })).toBeVisible();

    await pickerPage.goto('/field/truck');
    await expect(pickerPage).toHaveURL(/\/field\/truck/);
    await expect(pickerPage.getByRole('heading', { name: 'Technician Truck' })).toBeVisible();

    await pickerPage.goto('/settings');
    await expect(pickerPage).toHaveURL(/\/fulfillment/);
  });
});
