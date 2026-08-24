export const FINANCIAL_REPORT_ROLES = ['OWNER', 'ADMIN', 'FINANCE_ADMIN'] as const;
export const OPERATIONAL_REPORT_ROLES = ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'] as const;
export const REPORTS_PAGE_ROLES = [
  'OWNER',
  'ADMIN',
  'FINANCE_ADMIN',
  'WAREHOUSE_MANAGER',
] as const;

export type ReportTab =
  | 'valuation'
  | 'timeTravel'
  | 'turnover'
  | 'cogs'
  | 'profit'
  | 'sales'
  | 'fulfillment'
  | 'purchases'
  | 'returns'
  | 'demand'
  | 'labor'
  | 'audit';

export type ReportTabDef = {
  id: ReportTab;
  label: string;
  roles: readonly string[];
  /** Commercial module required to render this advanced report. */
  module?: string;
};

export const REPORT_TABS: ReportTabDef[] = [
  { id: 'valuation', label: 'Inventory valuation', roles: FINANCIAL_REPORT_ROLES },
  { id: 'timeTravel', label: 'Time-travel valuation', roles: FINANCIAL_REPORT_ROLES },
  { id: 'turnover', label: 'Stock turnover', roles: FINANCIAL_REPORT_ROLES },
  { id: 'cogs', label: 'COGS ledger', roles: FINANCIAL_REPORT_ROLES },
  { id: 'profit', label: 'Profit & margin', roles: FINANCIAL_REPORT_ROLES },
  { id: 'sales', label: 'Sales performance', roles: FINANCIAL_REPORT_ROLES },
  { id: 'fulfillment', label: 'Fulfillment', roles: OPERATIONAL_REPORT_ROLES },
  { id: 'purchases', label: 'Purchase spend', roles: FINANCIAL_REPORT_ROLES },
  {
    id: 'returns',
    label: 'Returns',
    roles: ['OWNER', 'ADMIN', 'FINANCE_ADMIN', 'WAREHOUSE_MANAGER'],
  },
  { id: 'demand', label: 'Demand sensing', roles: OPERATIONAL_REPORT_ROLES, module: 'MRP' },
  { id: 'labor', label: 'Labor & Velocity', roles: OPERATIONAL_REPORT_ROLES },
  { id: 'audit', label: 'Inventory Audit', roles: OPERATIONAL_REPORT_ROLES },
];

export function canAccessReportTab(
  tab: ReportTabDef,
  hasRole: (...roles: string[]) => boolean,
): boolean {
  return hasRole(...tab.roles);
}

export function reportTabNeedsUpgrade(
  tab: ReportTabDef,
  hasModule: (module: string) => boolean,
): boolean {
  return Boolean(tab.module && !hasModule(tab.module));
}

export function visibleReportTabs(
  hasRole: (...roles: string[]) => boolean,
): ReportTabDef[] {
  return REPORT_TABS.filter((tab) => canAccessReportTab(tab, hasRole));
}

export function defaultReportTab(visible: readonly ReportTabDef[]): ReportTab {
  const profit = visible.find((tab) => tab.id === 'profit');
  if (profit) return 'profit';
  return visible[0]?.id ?? 'fulfillment';
}

export function parseAllowedReportTab(
  raw: string | null,
  visible: readonly ReportTabDef[],
): ReportTab {
  if (raw && visible.some((tab) => tab.id === raw)) return raw as ReportTab;
  return defaultReportTab(visible);
}
