import { describe, expect, it } from 'vitest';
import {
  canAccessReportTab,
  defaultReportTab,
  parseAllowedReportTab,
  REPORT_TABS,
  reportTabNeedsUpgrade,
  visibleReportTabs,
} from './reportAccess';

describe('reportAccess', () => {
  const hasRole =
    (...held: string[]) =>
    (...needed: string[]) =>
      needed.some((role) => held.includes(role));
  const hasModule = (enabled: string[]) => (module: string) => enabled.includes(module);

  it('hides financial tabs from warehouse managers', () => {
    const visible = visibleReportTabs(hasRole('WAREHOUSE_MANAGER'));
    expect(visible.map((tab) => tab.id)).toEqual([
      'fulfillment',
      'returns',
      'demand',
      'labor',
      'audit',
    ]);
  });

  it('shows financial tabs to finance admins', () => {
    const visible = visibleReportTabs(hasRole('FINANCE_ADMIN'));
    expect(visible.some((tab) => tab.id === 'profit')).toBe(true);
    expect(visible.some((tab) => tab.id === 'fulfillment')).toBe(false);
  });

  it('keeps demand visible for entitled roles but flags a missing MRP module', () => {
    const demand = REPORT_TABS.find((tab) => tab.id === 'demand')!;
    expect(canAccessReportTab(demand, hasRole('OWNER'))).toBe(true);
    expect(reportTabNeedsUpgrade(demand, hasModule(['CORE']))).toBe(true);
    expect(reportTabNeedsUpgrade(demand, hasModule(['CORE', 'MRP']))).toBe(false);
  });

  it('defaults warehouse managers to fulfillment and rejects a financial deep-link', () => {
    const visible = visibleReportTabs(hasRole('WAREHOUSE_MANAGER'));
    expect(defaultReportTab(visible)).toBe('fulfillment');
    expect(parseAllowedReportTab('profit', visible)).toBe('fulfillment');
  });
});
