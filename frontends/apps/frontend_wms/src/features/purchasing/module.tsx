import { EnterpriseRouteGate } from '@/components/auth/EnterpriseRouteGate';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { InboundReceivePage } from '@/features/purchasing/InboundReceivePage';
import { MrpReorderWorkspace } from '@/features/purchasing/MrpReorderWorkspace';
import { PurchaseOrdersPage } from '@/features/purchasing/PurchaseOrdersPage';
import { RtvWorkspace } from '@/features/purchasing/RtvWorkspace';
import { SuppliersPage } from '@/features/purchasing/SuppliersPage';
import { ApDocumentWorkspace } from '@/pages/purchasing/ApDocumentWorkspace';
import { PurchaseOrderDetailPage } from '@/pages/purchasing/PurchaseOrderDetailPage';
import { SupplierDetailPage } from '@/pages/purchasing/SupplierDetailPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const purchasingModule = defineModule({
  id: 'purchasing',
  enabled: isModuleBuildEnabled('VITE_ENABLE_PURCHASING'),
  officeRoutes: [
    { path: 'purchase-orders', element: <PurchaseOrdersPage /> },
    { path: 'purchase-orders/:id', element: <PurchaseOrderDetailPage /> },
    { path: 'purchasing/orders/:id', element: <PurchaseOrderDetailPage /> },
    { path: 'suppliers', element: <SuppliersPage /> },
    { path: 'suppliers/:id', element: <SupplierDetailPage /> },
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
    {
      path: 'purchasing/ap-ingestion',
      element: (
        <EnterpriseRouteGate roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}>
          <ErrorBoundary boundaryName="ap-document-workspace">
            <ApDocumentWorkspace />
          </ErrorBoundary>
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
    { to: '/purchasing/ap-ingestion', label: 'AP Invoices', moduleId: 'purchasing' },
  ],
});
