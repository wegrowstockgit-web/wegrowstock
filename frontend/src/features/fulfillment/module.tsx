import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { CycleCountsPage } from '@/features/fulfillment/CycleCountsPage';
import { ExceptionsPage } from '@/features/fulfillment/ExceptionsPage';
import { FulfillmentPage } from '@/features/fulfillment/FulfillmentPage';
import { ReplenishmentsPage } from '@/features/fulfillment/ReplenishmentsPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const fulfillmentModule = defineModule({
  id: 'fulfillment',
  enabled: isModuleBuildEnabled('VITE_ENABLE_FULFILLMENT'),
  officeRoutes: [
    {
      path: 'exceptions',
      element: (
        <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
          <ExceptionsPage />
        </ProtectedRoute>
      ),
    },
  ],
  floorRoutes: [
    { path: '/fulfillment', element: <FulfillmentPage /> },
    { path: '/cycle-counts', element: <CycleCountsPage /> },
    { path: '/replenishments', element: <ReplenishmentsPage /> },
  ],
  navItems: [
    { to: '/fulfillment', label: 'Fulfillment', moduleId: 'fulfillment' },
    { to: '/replenishments', label: 'Replenishments', moduleId: 'fulfillment' },
    { to: '/cycle-counts', label: 'Cycle counts', moduleId: 'fulfillment' },
    { to: '/exceptions', label: 'Exceptions', moduleId: 'fulfillment' },
  ],
});
