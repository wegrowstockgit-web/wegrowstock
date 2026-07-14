import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';

import { AppShell } from '@/components/layout/AppShell';

import { ProtectedRoute } from '@/components/layout/ProtectedRoute';

import { LoginPage } from '@/pages/LoginPage';

import { SignupPage } from '@/pages/SignupPage';

import { InvitePage } from '@/pages/InvitePage';

import { DashboardPage } from '@/pages/DashboardPage';

import { ProductsPage } from '@/pages/ProductsPage';

import { FulfillmentPage } from '@/pages/FulfillmentPage';

import { CycleCountsPage } from '@/pages/CycleCountsPage';

import { SupplierPortalPage } from '@/pages/SupplierPortalPage';

import { SettingsPage } from '@/pages/SettingsPage';

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

import { useSessionStore, useIsAuthenticated } from '@/stores/session';



function RootRedirect() {

  const authenticated = useIsAuthenticated();

  const isB2bCustomerOnly = useSessionStore((s) => s.isB2bCustomerOnly);

  if (!authenticated) {

    return <Navigate to="/login" replace />;

  }

  const isPickerOnly = useSessionStore.getState().isPickerOnly();

  return (

    <Navigate
      to={
        isB2bCustomerOnly()
          ? '/showroom/catalog'
          : isPickerOnly
            ? '/fulfillment'
            : '/dashboard'
      }
      replace
    />

  );

}



export function App() {

  return (

    <BrowserRouter>

      <Routes>

        <Route path="/login" element={<LoginPage />} />

        <Route path="/signup" element={<SignupPage />} />

        <Route path="/invite/:token" element={<InvitePage />} />

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
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
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

        </Route>



        <Route path="*" element={<Navigate to="/" replace />} />

      </Routes>

    </BrowserRouter>

  );

}

