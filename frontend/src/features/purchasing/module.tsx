import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { InboundReceivePage } from '@/features/purchasing/InboundReceivePage';
import { MrpReorderWorkspace } from '@/features/purchasing/MrpReorderWorkspace';
import { PurchaseOrdersPage } from '@/features/purchasing/PurchaseOrdersPage';
import { SuppliersPage } from '@/features/purchasing/SuppliersPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const purchasingModule = defineModule({
  id: 'purchasing',
  enabled: isModuleBuildEnabled('VITE_ENABLE_PURCHASING'),
  officeRoutes: [
    { path: 'purchase-orders', element: <PurchaseOrdersPage /> },
    { path: 'suppliers', element: <SuppliersPage /> },
    {
      path: 'mrp',
      element: (
        <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}>
          <MrpReorderWorkspace />
        </ProtectedRoute>
      ),
    },
  ],
  standaloneRoutes: [
    {
      path: '/inbound/receive',
      element: (
        <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
          <ErrorBoundary boundaryName="inbound-receive">
            <InboundReceivePage />
          </ErrorBoundary>
        </ProtectedRoute>
      ),
    },
  ],
  navItems: [
    { to: '/purchase-orders', label: 'Purchase Orders', moduleId: 'purchasing' },
    { to: '/suppliers', label: 'Suppliers', moduleId: 'purchasing' },
    { to: '/mrp', label: 'MRP reorder', moduleId: 'purchasing' },
  ],
});
