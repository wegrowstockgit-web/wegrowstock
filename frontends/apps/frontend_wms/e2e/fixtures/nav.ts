import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/** Map leaf labels → parent category button name in the grouped icon-rail. */
export const NAV_CATEGORY_BY_LINK: Record<string, string> = {
  'Purchase Orders': 'Inbound',
  Suppliers: 'Inbound',
  Returns: 'Inbound',
  'Sales Orders': 'Outbound',
  Customers: 'Outbound',
  Invoices: 'Outbound',
  Fulfillment: 'Outbound',
  Products: 'Inventory',
  'Cycle counts': 'Inventory',
  Replenishments: 'Inventory',
  Exceptions: 'Inventory',
  'Lot Trace': 'Inventory',
  Reports: 'Admin',
  'RTLS map': 'Admin',
  Organization: 'Admin',
  Settings: 'Admin', // legacy label → Organization leaf
  BOMs: 'Manufacturing',
  Manufacturing: 'Manufacturing', // legacy label → BOMs group
  'Production Orders': 'Manufacturing',
  'Issue Supplies': 'Field',
  'Technician Truck': 'Field',
};

export async function iconRail(page: Page) {
  return page.getByTestId('icon-rail');
}

/** Expand a primary category if its panel is collapsed. */
export async function expandNavCategory(page: Page, category: string): Promise<void> {
  const rail = await iconRail(page);
  await expect(rail).toBeVisible({ timeout: 15_000 });

  const id =
    category === 'Inbound'
      ? 'inbound'
      : category === 'Outbound'
        ? 'outbound'
        : category === 'Inventory'
          ? 'inventory'
          : category === 'Manufacturing'
            ? 'manufacturing'
            : category === 'Field'
              ? 'field'
              : category === 'Admin'
                ? 'admin'
                : category.toLowerCase();

  const group = rail.getByTestId(`nav-group-${id}`);
  const toggle = rail.getByTestId(`nav-category-${id}`);
  await expect(toggle).toBeVisible();

  if ((await group.getAttribute('data-open')) !== 'true') {
    await toggle.click();
  }
  await expect(group).toHaveAttribute('data-open', 'true');
}

/**
 * Click a nested (or top-level) rail link. Nested links expand their category first.
 * `Manufacturing` legacy name resolves to the BOMs leaf.
 */
export async function clickNavLink(page: Page, linkName: string): Promise<void> {
  const rail = await iconRail(page);
  await expect(rail).toBeVisible({ timeout: 15_000 });

  const resolvedName =
    linkName === 'Manufacturing' ? 'BOMs' : linkName === 'Settings' ? 'Organization' : linkName;
  const category = NAV_CATEGORY_BY_LINK[linkName] ?? NAV_CATEGORY_BY_LINK[resolvedName];

  if (category) {
    await expandNavCategory(page, category);
  }

  await rail.getByRole('link', { name: resolvedName, exact: true }).click();
}

/** Assert a nested leaf is visible (expands parent when needed). */
export async function expectNavLinkVisible(page: Page, linkName: string): Promise<void> {
  const rail = await iconRail(page);
  const resolvedName =
    linkName === 'Manufacturing' ? 'BOMs' : linkName === 'Settings' ? 'Organization' : linkName;
  const category = NAV_CATEGORY_BY_LINK[linkName] ?? NAV_CATEGORY_BY_LINK[resolvedName];
  if (category) {
    await expandNavCategory(page, category);
  }
  await expect(rail.getByRole('link', { name: resolvedName, exact: true })).toBeVisible();
}

/** Assert a leaf is absent even after expanding its category (if the category exists). */
export async function expectNavLinkHidden(page: Page, linkName: string): Promise<void> {
  const rail = await iconRail(page);
  const resolvedName =
    linkName === 'Manufacturing' ? 'BOMs' : linkName === 'Settings' ? 'Organization' : linkName;
  const category = NAV_CATEGORY_BY_LINK[linkName] ?? NAV_CATEGORY_BY_LINK[resolvedName];
  if (category) {
    const id = category.toLowerCase();
    const toggle = rail.getByTestId(`nav-category-${id}`);
    if ((await toggle.count()) > 0) {
      await expandNavCategory(page, category);
    }
  }
  await expect(rail.getByRole('link', { name: resolvedName, exact: true })).toHaveCount(0);
}
