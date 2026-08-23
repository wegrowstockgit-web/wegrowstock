import { CustomersPage } from '@/features/sales/CustomersPage';
import { InvoicesPage } from '@/features/sales/InvoicesPage';
import { SalesOrdersPage } from '@/features/sales/SalesOrdersPage';
import { CustomerWorkspacePage } from '@/pages/sales/CustomerWorkspacePage';
import { InvoiceDetailPage } from '@/pages/sales/InvoiceDetailPage';
import { NewSalesOrderPage } from '@/pages/sales/NewSalesOrderPage';
import { SalesOrderDetailPage } from '@/pages/sales/SalesOrderDetailPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const salesModule = defineModule({
  id: 'sales',
  enabled: isModuleBuildEnabled('VITE_ENABLE_SALES'),
  officeRoutes: [
    { path: 'sales-orders', element: <SalesOrdersPage /> },
    { path: 'sales-orders/new', element: <NewSalesOrderPage /> },
    { path: 'sales-orders/:id', element: <SalesOrderDetailPage /> },
    { path: 'sales/orders', element: <SalesOrdersPage /> },
    { path: 'sales/orders/new', element: <NewSalesOrderPage /> },
    { path: 'sales/orders/:id', element: <SalesOrderDetailPage /> },
    { path: 'invoices', element: <InvoicesPage /> },
    { path: 'invoices/:id', element: <InvoiceDetailPage /> },
    { path: 'customers', element: <CustomersPage /> },
    { path: 'sales/customers', element: <CustomersPage /> },
    { path: 'sales/customers/:id', element: <CustomerWorkspacePage /> },
  ],
  navItems: [
    { to: '/sales-orders', label: 'Sales Orders', moduleId: 'sales' },
    { to: '/customers', label: 'Customers', moduleId: 'sales' },
    { to: '/invoices', label: 'Invoices', moduleId: 'sales' },
  ],
});
