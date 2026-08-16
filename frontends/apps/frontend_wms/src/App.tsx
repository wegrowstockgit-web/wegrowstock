import type { ReactElement, ReactNode } from 'react';
import { useMemo } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom';
import { IS_CHATBOT_ENABLED, isChatbotEnabled, IS_TRAINING_ENABLED, isTrainingEnabled } from '@/lib/featureFlags';
import { ChatbotHost } from '@/lib/chatbot/active';
import { TrainingHost } from '@/lib/training/active';
import '@/lib/router/appModules';
import {
  getEnabledFloorRoutes,
  getEnabledOfficeRoutes,
  getEnabledStandaloneRoutes,
} from '@/lib/router/moduleRegistry';

import { AppShell } from '@/components/layout/AppShell';
import { WarehouseFloorShell } from '@/components/layout/WarehouseFloorShell';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { LoginPage } from '@/pages/LoginPage';
import { SignupPage } from '@/pages/SignupPage';
import { InvitePage } from '@/pages/InvitePage';
import { DashboardPage } from '@/pages/DashboardPage';
import { ImportPage } from '@/pages/ImportPage';
import { SupplierPortalPage } from '@/pages/SupplierPortalPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { ProfileSettingsPage } from '@/pages/ProfileSettingsPage';
import { AddWarehousePage } from '@/pages/AddWarehousePage';
import { BillingSettingsPage } from '@/pages/BillingSettingsPage';
import { IntegrationsHubPage } from '@/pages/IntegrationsHubPage';
import { RtlsWorkspacePage } from '@/pages/RtlsWorkspacePage';
import { ManufacturingBomsPage } from '@/pages/ManufacturingBomsPage';
import { ManufacturingOrdersPage } from '@/pages/ManufacturingOrdersPage';
import { ProductionTerminalPage } from '@/pages/ProductionTerminalPage';
import { ReturnsPage } from '@/pages/ReturnsPage';
import { ReturnsReceivePage } from '@/pages/ReturnsReceivePage';
import { ReportsPage } from '@/pages/ReportsPage';
import { IssueSuppliesPage } from '@/pages/IssueSuppliesPage';
import { LotTracePage } from '@/pages/LotTracePage';
import { TechnicianTruckPage } from '@/pages/TechnicianTruckPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ShowroomLayout } from '@/pages/showroom/ShowroomLayout';
import { ShowroomCatalogPage } from '@/pages/showroom/ShowroomCatalogPage';
import { ShowroomOrdersPage } from '@/pages/showroom/ShowroomOrdersPage';
import { ShowroomCheckoutPage } from '@/pages/showroom/ShowroomCheckoutPage';
import { ShowroomBillingPage } from '@/pages/showroom/ShowroomBillingPage';
import { ShowroomApplyPage } from '@/pages/showroom/ShowroomApplyPage';
import { ShowroomLoginPage } from '@/pages/showroom/ShowroomLoginPage';
import { ScannerSecurityGate } from '@/components/security/ScannerSecurityGate';
import {
  useIsAuthenticated,
  useSessionRoles,
  isExclusiveRole,
  useEnabledModules,
} from '@/stores/session';
import { PLAYBOOK_ROUTE_ALIASES } from '@/lib/pageKnowledge/playbookAliases';

/** Prompt alias: /invite/accept?token=… → /invite/:token */
function InviteAcceptRedirect() {
  const [params] = useSearchParams();
  const token = params.get('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={`/invite/${token}`} replace />;
}

function AliasRedirect({ to }: { to: string }) {
  const [params] = useSearchParams();
  const [path, existingQs] = to.split('?');
  const merged = new URLSearchParams(existingQs ?? '');
  params.forEach((value, key) => {
    if (!merged.has(key)) {
      merged.set(key, value);
    }
  });
  const qs = merged.toString();
  return <Navigate to={qs ? `${path}?${qs}` : path} replace />;
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

function renderRoute(route: {
  path?: string;
  index?: boolean;
  element?: ReactNode;
  children?: unknown;
}) {
  return (
    <Route
      key={route.path ?? (route.index ? 'index' : 'route')}
      path={route.path}
      index={route.index}
      element={route.element as ReactElement | undefined}
    />
  );
}

export function App() {
  const entitlements = useEnabledModules();
  const officeFeatureRoutes = useMemo(
    () => getEnabledOfficeRoutes(entitlements),
    [entitlements],
  );
  const floorFeatureRoutes = useMemo(
    () => getEnabledFloorRoutes(entitlements),
    [entitlements],
  );
  const standaloneFeatureRoutes = useMemo(
    () => getEnabledStandaloneRoutes(entitlements),
    [entitlements],
  );

  return (
    <BrowserRouter>
      <ScannerSecurityGate>
        <ErrorBoundary boundaryName="app-root">
          {/* Optional: stubbed when chatbot module disabled/absent (scripts/resolve-chatbot.mjs) */}
          {IS_CHATBOT_ENABLED && isChatbotEnabled() ? <ChatbotHost /> : null}
          {IS_TRAINING_ENABLED && isTrainingEnabled() ? <TrainingHost /> : null}
          <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/showroom/login" element={<ShowroomLoginPage />} />
          <Route path="/showroom/apply" element={<ShowroomApplyPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/invite/:token" element={<InvitePage />} />
          <Route path="/invite/accept" element={<InviteAcceptRedirect />} />
          <Route path="/supplier-portal/po/:token" element={<SupplierPortalPage />} />
          {PLAYBOOK_ROUTE_ALIASES.map(({ from, to }) => (
            <Route key={from} path={from} element={<AliasRedirect to={to} />} />
          ))}

          {standaloneFeatureRoutes.map((route) => renderRoute(route))}

          {/* Floor ops — WarehouseFloorShell (no corporate Sidebar) */}
          <Route
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER']}>
                <WarehouseFloorShell />
              </ProtectedRoute>
            }
          >
            {floorFeatureRoutes.map((route) => renderRoute(route))}
            <Route path="/manufacturing/terminal" element={<ProductionTerminalPage />} />
            <Route path="/returns/receive" element={<ReturnsReceivePage />} />
            <Route path="/issue-supplies" element={<IssueSuppliesPage />} />
            <Route path="/field/truck" element={<TechnicianTruckPage />} />
          </Route>

          <Route
            path="/showroom"
            element={
              <ErrorBoundary boundaryName="showroom">
                <ShowroomLayout />
              </ErrorBoundary>
            }
          >
            <Route index element={<Navigate to="catalog" replace />} />
            <Route path="catalog" element={<ShowroomCatalogPage />} />
            <Route
              path="orders"
              element={
                <ProtectedRoute b2bOnly>
                  <ShowroomOrdersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="checkout"
              element={
                <ProtectedRoute b2bOnly>
                  <ShowroomCheckoutPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="billing"
              element={
                <ProtectedRoute b2bOnly>
                  <ShowroomBillingPage />
                </ProtectedRoute>
              }
            />
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
            {officeFeatureRoutes.map((route) => renderRoute(route))}
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
              path="returns"
              element={
                <ProtectedRoute roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']} officeOnly>
                  <ReturnsPage />
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
            {/* Auth-gated probe for ErrorBoundary e2e — not linked in navigation. */}
            <Route path="__e2e/crash" element={<E2eCrashProbe />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </ErrorBoundary>
      </ScannerSecurityGate>
    </BrowserRouter>
  );
}
