import { CustomersPage } from '@/features/sales/CustomersPage';
import { InvoicesPage } from '@/features/sales/InvoicesPage';
import { SalesOrdersPage } from '@/features/sales/SalesOrdersPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const salesModule = defineModule({
  id: 'sales',
  enabled: isModuleBuildEnabled('VITE_ENABLE_SALES'),
  officeRoutes: [
    { path: 'sales-orders', element: <SalesOrdersPage /> },
    { path: 'sales/orders', element: <SalesOrdersPage /> },
    { path: 'invoices', element: <InvoicesPage /> },
    { path: 'customers', element: <CustomersPage /> },
    { path: 'sales/customers', element: <CustomersPage /> },
  ],
  navItems: [
    { to: '/sales-orders', label: 'Sales Orders', moduleId: 'sales' },
    { to: '/customers', label: 'Customers', moduleId: 'sales' },
    { to: '/invoices', label: 'Invoices', moduleId: 'sales' },
  ],
});
