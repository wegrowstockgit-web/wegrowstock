import { expect, test } from './fixtures/roleFixture';
import { expectNavLinkHidden, expectNavLinkVisible } from './fixtures/nav';

test.describe('Pillar role visibility', () => {
  test('owner sees Compliance, Issue Supplies, and Settings cost centers tab', async ({
    ownerPage,
  }) => {
    await ownerPage.goto('/dashboard');
    await expectNavLinkVisible(ownerPage, 'Lot Trace');
    await expectNavLinkVisible(ownerPage, 'Issue Supplies');

    await ownerPage.goto('/settings');
    await expect(ownerPage.getByRole('button', { name: 'Cost Centers & Requisitions' })).toBeVisible();
  });

  test('viewer sees Compliance but not Issue Supplies nav or Issue Fact', async ({ viewerPage }) => {
    await viewerPage.goto('/dashboard');
    await expectNavLinkVisible(viewerPage, 'Lot Trace');
    await expectNavLinkHidden(viewerPage, 'Issue Supplies');

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
