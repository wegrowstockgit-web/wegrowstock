/**
 * Intentional help-action shortcuts. Nested paths in the knowledge dictionary
 * land on real screens that already exist in the WMS.
 */
export const PLAYBOOK_ROUTE_ALIASES: ReadonlyArray<{ from: string; to: string }> = [
  { from: '/tasks/my-queue', to: '/fulfillment' },
  { from: '/dashboard/labor', to: '/dashboard' },
  { from: '/sales-orders/new', to: '/sales-orders' },
  { from: '/fulfillment/waves/new', to: '/fulfillment' },
  { from: '/fulfillment/pick', to: '/fulfillment' },
  { from: '/fulfillment/pack', to: '/fulfillment' },
  { from: '/purchase-orders/new', to: '/purchase-orders' },
  { from: '/purchasing/orders', to: '/purchase-orders' },
  { from: '/inbound/receive/scan', to: '/inbound/receive' },
  { from: '/inbound/putaway', to: '/replenishments' },
  { from: '/products/new', to: '/products' },
  { from: '/cycle-counts/new', to: '/cycle-counts' },
  { from: '/cycle-counts/variances', to: '/cycle-counts' },
  { from: '/exceptions/pending', to: '/exceptions' },
  { from: '/returns/vendor', to: '/purchasing/rtv' },
  { from: '/settings/scanner', to: '/settings?tab=operations' },
  { from: '/settings/roles', to: '/settings?tab=users' },
  { from: '/inventory', to: '/products' },
];
