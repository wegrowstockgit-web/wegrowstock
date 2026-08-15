import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { ClusterPickerView } from '@/features/fulfillment/ClusterPickerView';
import { CycleCountsPage } from '@/features/fulfillment/CycleCountsPage';
import { DockScheduleCalendar } from '@/features/fulfillment/DockScheduleCalendar';
import { ExceptionsPage } from '@/features/fulfillment/ExceptionsPage';
import { FulfillmentPage } from '@/features/fulfillment/FulfillmentPage';
import { PalletManifestWorkspace } from '@/features/fulfillment/PalletManifestWorkspace';
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
    {
      path: 'dock-schedule',
      element: (
        <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}>
          <DockScheduleCalendar />
        </ProtectedRoute>
      ),
    },
  ],
  floorRoutes: [
    { path: '/fulfillment', element: <FulfillmentPage /> },
    { path: '/cycle-counts', element: <CycleCountsPage /> },
    { path: '/replenishments', element: <ReplenishmentsPage /> },
    { path: '/cluster-pick', element: <ClusterPickerView /> },
    { path: '/pallet-manifests', element: <PalletManifestWorkspace /> },
  ],
  navItems: [
    { to: '/fulfillment', label: 'Fulfillment', moduleId: 'fulfillment' },
    { to: '/replenishments', label: 'Replenishments', moduleId: 'fulfillment' },
    { to: '/cycle-counts', label: 'Cycle counts', moduleId: 'fulfillment' },
    { to: '/cluster-pick', label: 'Cluster pick', moduleId: 'fulfillment' },
    { to: '/pallet-manifests', label: 'Pallet manifests', moduleId: 'fulfillment' },
    { to: '/exceptions', label: 'Exceptions', moduleId: 'fulfillment' },
    { to: '/dock-schedule', label: 'Dock schedule', moduleId: 'fulfillment' },
  ],
});
