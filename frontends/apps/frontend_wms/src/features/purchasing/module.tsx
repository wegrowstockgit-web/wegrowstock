import { EnterpriseRouteGate } from '@/components/auth/EnterpriseRouteGate';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { InboundReceivePage } from '@/features/purchasing/InboundReceivePage';
import { MrpReorderWorkspace } from '@/features/purchasing/MrpReorderWorkspace';
import { PurchaseOrdersPage } from '@/features/purchasing/PurchaseOrdersPage';
import { RtvWorkspace } from '@/features/purchasing/RtvWorkspace';
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
        <EnterpriseRouteGate
          requiredModule="MRP"
          requiredPermission={['mrp:run']}
          roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}
        >
          <MrpReorderWorkspace />
        </EnterpriseRouteGate>
      ),
    },
    {
      path: 'purchasing/rtv',
      element: (
        <EnterpriseRouteGate roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}>
          <RtvWorkspace />
        </EnterpriseRouteGate>
      ),
    },
  ],
  standaloneRoutes: [
    {
      path: '/inbound/receive',
      element: (
        <EnterpriseRouteGate roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
          <ErrorBoundary boundaryName="inbound-receive">
            <InboundReceivePage />
          </ErrorBoundary>
        </EnterpriseRouteGate>
      ),
    },
  ],
  navItems: [
    { to: '/purchase-orders', label: 'Purchase Orders', moduleId: 'purchasing' },
    { to: '/suppliers', label: 'Suppliers', moduleId: 'purchasing' },
    { to: '/mrp', label: 'MRP reorder', moduleId: 'purchasing' },
    { to: '/purchasing/rtv', label: 'RTV / Chargebacks', moduleId: 'purchasing' },
  ],
});
