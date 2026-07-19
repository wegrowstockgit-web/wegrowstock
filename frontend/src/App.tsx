import type { ReactElement } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom';

import { AppShell } from '@/components/layout/AppShell';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { LoginPage } from '@/pages/LoginPage';
import { SignupPage } from '@/pages/SignupPage';
import { InvitePage } from '@/pages/InvitePage';
import { DashboardPage } from '@/pages/DashboardPage';
import { ProductsPage } from '@/pages/ProductsPage';
import { FulfillmentPage } from '@/pages/FulfillmentPage';
import { ExceptionsPage } from '@/pages/ExceptionsPage';
import { ImportPage } from '@/pages/ImportPage';
import { CycleCountsPage } from '@/pages/CycleCountsPage';
import { SupplierPortalPage } from '@/pages/SupplierPortalPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { ProfileSettingsPage } from '@/pages/ProfileSettingsPage';
import { AddWarehousePage } from '@/pages/AddWarehousePage';
import { BillingSettingsPage } from '@/pages/BillingSettingsPage';
import { FintechSettingsPage } from '@/pages/FintechSettingsPage';
import { IntegrationsHubPage } from '@/pages/IntegrationsHubPage';
import { RtlsWorkspacePage } from '@/pages/RtlsWorkspacePage';
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
import { InboundReceivePage } from '@/pages/InboundReceivePage';
import { ReportsPage } from '@/pages/ReportsPage';
import { IssueSuppliesPage } from '@/pages/IssueSuppliesPage';
import { ReplenishmentsPage } from '@/pages/ReplenishmentsPage';
import { LotTracePage } from '@/pages/LotTracePage';
import { TechnicianTruckPage } from '@/pages/TechnicianTruckPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ShowroomLayout } from '@/pages/showroom/ShowroomLayout';
import { ShowroomCatalogPage } from '@/pages/showroom/ShowroomCatalogPage';
import { ShowroomOrdersPage } from '@/pages/showroom/ShowroomOrdersPage';
import { ShowroomCheckoutPage } from '@/pages/showroom/ShowroomCheckoutPage';
import { ShowroomBillingPage } from '@/pages/showroom/ShowroomBillingPage';
import { useIsAuthenticated, useSessionRoles, isExclusiveRole } from '@/stores/session';

/** Prompt alias: /invite/accept?token=… → /invite/:token */
function InviteAcceptRedirect() {
  const [params] = useSearchParams();
  const token = params.get('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={`/invite/${token}`} replace />;
}

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

/** Intentional render crash for e2e / vitest — not linked in navigation. */
function E2eCrashProbe(): ReactElement {
  throw new Error('E2E intentional render crash');
  // Unreachable — satisfies ReactElement return for tsc under noImplicitReturns.
  return <div />;
}

export function App() {
  return (
    <BrowserRouter>
      <ErrorBoundary boundaryName="app-root">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/invite/:token" element={<InvitePage />} />
          <Route path="/invite/accept" element={<InviteAcceptRedirect />} />
          <Route path="/supplier-portal/po/:token" element={<SupplierPortalPage />} />

          {/* Full-screen mobile inbound — outside AppShell / office chrome */}
          <Route
            path="/inbound/receive"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                <ErrorBoundary boundaryName="inbound-receive">
                  <InboundReceivePage />
                </ErrorBoundary>
              </ProtectedRoute>
            }
          />

          <Route
            path="/showroom"
            element={
              <ProtectedRoute b2bOnly>
                <ErrorBoundary boundaryName="showroom">
                  <ShowroomLayout />
                </ErrorBoundary>
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="catalog" replace />} />
            <Route path="catalog" element={<ShowroomCatalogPage />} />
            <Route path="orders" element={<ShowroomOrdersPage />} />
            <Route path="checkout" element={<ShowroomCheckoutPage />} />
            <Route path="billing" element={<ShowroomBillingPage />} />
            <Route path="*" element={<NotFoundPage />} />
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
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
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
                <ProtectedRoute roles={['OWNER', 'ADMIN']} officeOnly>
                  <ImportPage legacy />
                </ProtectedRoute>
              }
            />
            <Route
              path="cycle-counts"
              element={
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
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
              path="warehouses/add"
              element={
                <ProtectedRoute roles={['OWNER', 'ADMIN']} officeOnly>
                  <AddWarehousePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="rtls"
              element={
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}>
                  <RtlsWorkspacePage />
                </ProtectedRoute>
              }
            />
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
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                  <IssueSuppliesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="replenishments"
              element={
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                  <ReplenishmentsPage />
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
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                  <TechnicianTruckPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="settings/profile"
              element={
                <ProtectedRoute
                  roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER']}
                >
                  <ProfileSettingsPage />
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
              path="settings/operations"
              element={
                <ProtectedRoute roles={['ADMIN', 'OWNER']} officeOnly>
                  <Navigate to="/settings?tab=operations" replace />
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
              path="settings/integrations"
              element={
                <ProtectedRoute roles={['OWNER', 'ADMIN']} officeOnly>
                  <IntegrationsHubPage />
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
            {/* Auth-gated probe for ErrorBoundary e2e — not linked in navigation. */}
            <Route path="__e2e/crash" element={<E2eCrashProbe />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </ErrorBoundary>
    </BrowserRouter>
  );
}
