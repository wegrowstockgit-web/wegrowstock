import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom';

import { AppShell } from '@/components/layout/AppShell';

import { ProtectedRoute } from '@/components/layout/ProtectedRoute';

import { LoginPage } from '@/pages/LoginPage';

import { SignupPage } from '@/pages/SignupPage';

import { InvitePage } from '@/pages/InvitePage';

/** Prompt alias: /invite/accept?token=… → /invite/:token */
function InviteAcceptRedirect() {
  const [params] = useSearchParams();
  const token = params.get('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={`/invite/${token}`} replace />;
}

import { DashboardPage } from '@/pages/DashboardPage';

import { ProductsPage } from '@/pages/ProductsPage';

import { FulfillmentPage } from '@/pages/FulfillmentPage';

import { ExceptionsPage } from '@/pages/ExceptionsPage';

import { ImportPage } from '@/pages/ImportPage';

import { CycleCountsPage } from '@/pages/CycleCountsPage';

import { SupplierPortalPage } from '@/pages/SupplierPortalPage';

import { SettingsPage } from '@/pages/SettingsPage';

import { BillingSettingsPage } from '@/pages/BillingSettingsPage';

import { FintechSettingsPage } from '@/pages/FintechSettingsPage';

import { PurchaseOrdersPage } from '@/pages/PurchaseOrdersPage';

import { SalesOrdersPage } from '@/pages/SalesOrdersPage';

import { InvoicesPage } from '@/pages/InvoicesPage';

import { CustomersPage } from '@/pages/CustomersPage';

import { SuppliersPage } from '@/pages/SuppliersPage';

import { ManufacturingBomsPage } from '@/pages/ManufacturingBomsPage';

import { ManufacturingOrdersPage } from '@/pages/ManufacturingOrdersPage';

import { ProductionTerminalPage } from '@/pages/ProductionTerminalPage';

import { ReturnsPage } from '@/pages/ReturnsPage';

import { ReturnsReceivePage } from '@/pages/ReturnsReceivePage';

import { ReportsPage } from '@/pages/ReportsPage';

import { IssueSuppliesPage } from '@/pages/IssueSuppliesPage';

import { LotTracePage } from '@/pages/LotTracePage';

import { TechnicianTruckPage } from '@/pages/TechnicianTruckPage';

import { ShowroomLayout } from '@/pages/showroom/ShowroomLayout';

import { ShowroomCatalogPage } from '@/pages/showroom/ShowroomCatalogPage';

import { ShowroomOrdersPage } from '@/pages/showroom/ShowroomOrdersPage';

import { ShowroomCheckoutPage } from '@/pages/showroom/ShowroomCheckoutPage';

import { ShowroomBillingPage } from '@/pages/showroom/ShowroomBillingPage';

import { useIsAuthenticated, useSessionRoles, isExclusiveRole } from '@/stores/session';

function RootRedirect() {
  const authenticated = useIsAuthenticated();
  const sessionRoles = useSessionRoles();

  if (!authenticated) {
    return <Navigate to="/login" replace />;
  }

  const destination = isExclusiveRole(sessionRoles, 'B2B_CUSTOMER')
    ? '/showroom/catalog'
    : isExclusiveRole(sessionRoles, 'PICKER')
      ? '/fulfillment'
      : '/dashboard';

  return <Navigate to={destination} replace />;
}



export function App() {

  return (

    <BrowserRouter>

      <Routes>

        <Route path="/login" element={<LoginPage />} />

        <Route path="/signup" element={<SignupPage />} />

        <Route path="/invite/:token" element={<InvitePage />} />
        <Route
          path="/invite/accept"
          element={<InviteAcceptRedirect />}
        />

        <Route path="/supplier-portal/po/:token" element={<SupplierPortalPage />} />



        <Route

          path="/showroom"

          element={

            <ProtectedRoute b2bOnly>

              <ShowroomLayout />

            </ProtectedRoute>

          }

        >

          <Route index element={<Navigate to="catalog" replace />} />

          <Route path="catalog" element={<ShowroomCatalogPage />} />

          <Route path="orders" element={<ShowroomOrdersPage />} />

          <Route path="checkout" element={<ShowroomCheckoutPage />} />

          <Route path="billing" element={<ShowroomBillingPage />} />

        </Route>



        <Route

          path="/"

          element={

            <ProtectedRoute officeOnly>

              <AppShell />

            </ProtectedRoute>

          }

        >

          <Route index element={<RootRedirect />} />

          <Route path="dashboard" element={<DashboardPage />} />

          <Route path="products" element={<ProductsPage />} />

          <Route
            path="fulfillment"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']} officeOnly>
                <FulfillmentPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="exceptions"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                <ExceptionsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="import"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                <ImportPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="settings/import"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                <ImportPage legacy />
              </ProtectedRoute>
            }
          />

          <Route
            path="cycle-counts"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']} officeOnly>
                <CycleCountsPage />
              </ProtectedRoute>
            }
          />

          <Route path="purchase-orders" element={<PurchaseOrdersPage />} />

          <Route path="sales-orders" element={<SalesOrdersPage />} />

          <Route path="invoices" element={<InvoicesPage />} />

          <Route
            path="reports"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN']} officeOnly>
                <ReportsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="reports/audit-log"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN']} officeOnly>
                <Navigate to="/settings?tab=operations" replace />
              </ProtectedRoute>
            }
          />

          <Route path="customers" element={<CustomersPage />} />

          <Route path="suppliers" element={<SuppliersPage />} />

          <Route
            path="manufacturing/boms"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                <ManufacturingBomsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="manufacturing/orders"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                <ManufacturingOrdersPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="manufacturing/terminal"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                <ProductionTerminalPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="returns"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                <ReturnsPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="returns/receive"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                <ReturnsReceivePage />
              </ProtectedRoute>
            }
          />

          <Route
            path="issue-supplies"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']} officeOnly>
                <IssueSuppliesPage />
              </ProtectedRoute>
            }
          />

          <Route
            path="compliance/lot-trace"
            element={
              <ProtectedRoute
                roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER']}
                officeOnly
              >
                <LotTracePage />
              </ProtectedRoute>
            }
          />

          <Route
            path="field/truck"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']} officeOnly>
                <TechnicianTruckPage />
              </ProtectedRoute>
            }
          />

          <Route

            path="settings"

            element={

              <ProtectedRoute roles={['ADMIN', 'OWNER']} officeOnly>

                <SettingsPage />

              </ProtectedRoute>

            }

          />

          <Route
            path="settings/users"
            element={
              <ProtectedRoute roles={['ADMIN', 'OWNER']} officeOnly>
                <Navigate to="/settings?tab=users" replace />
              </ProtectedRoute>
            }
          />

          <Route

            path="settings/billing"

            element={

              <ProtectedRoute roles={['ADMIN', 'OWNER']} officeOnly>

                <BillingSettingsPage />

              </ProtectedRoute>

            }

          />

          <Route

            path="settings/fintech"

            element={

              <ProtectedRoute roles={['OWNER']} officeOnly>

                <FintechSettingsPage />

              </ProtectedRoute>

            }

          />

        </Route>



        <Route path="*" element={<Navigate to="/" replace />} />

      </Routes>

    </BrowserRouter>

  );

}

