import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useEntitlement } from '@/hooks/useEntitlement';
import { NotFoundPage } from '@/pages/NotFoundPage';
import {
  isExclusiveRole,
  rolesInclude,
  useIsAuthenticated,
  useSessionHydrated,
  useSessionRoles,
  useSessionStore,
} from '@/stores/session';

export type EnterpriseRouteGateProps = {
  requiredModule?: string;
  /** Entitled if the tenant owns any of these commercial modules. */
  anyOfModules?: readonly string[];
  /** User must hold every listed permission (OWNER always passes). */
  requiredPermission?: string[];
  /** Role allow-list (legacy RBAC). Missing roles redirect like the previous office walls. */
  roles?: string[];
  officeOnly?: boolean;
  b2bOnly?: boolean;
  children?: ReactNode;
};

function hasCommercialAccess(
  hasModule: (name: string) => boolean,
  requiredModule?: string,
  anyOfModules?: readonly string[],
): boolean {
  if (requiredModule) return hasModule(requiredModule);
  if (anyOfModules?.length) return anyOfModules.some((name) => hasModule(name));
  return true;
}

/**
 * Unified commercial-tier + RBAC layout gate.
 *
 * Evaluation order: auth → commercial module → permission → outlet.
 */
export function EnterpriseRouteGate({
  requiredModule,
  anyOfModules,
  requiredPermission,
  roles,
  officeOnly = false,
  b2bOnly = false,
  children,
}: EnterpriseRouteGateProps) {
  const hydrated = useSessionHydrated();
  const authenticated = useIsAuthenticated();
  const sessionRoles = useSessionRoles();
  const hasPermission = useSessionStore((s) => s.hasPermission);
  const { hasModule } = useEntitlement();
  const location = useLocation();

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface text-sm text-text-muted">
        Loading session…
      </div>
    );
  }

  // STEP 1 — Auth
  if (!authenticated) {
    return (
      <Navigate
        to={b2bOnly ? '/showroom/login' : '/login'}
        state={{ from: location }}
        replace
      />
    );
  }

  const b2bOnlyUser = isExclusiveRole(sessionRoles, 'B2B_CUSTOMER');
  const pickerOnly = isExclusiveRole(sessionRoles, 'PICKER');

  if (b2bOnly && !rolesInclude(sessionRoles, 'B2B_CUSTOMER')) {
    return <Navigate to="/dashboard" replace />;
  }

  if (officeOnly && b2bOnlyUser) {
    return <Navigate to="/showroom/catalog" replace />;
  }

  // STEP 2 — Commercial entitlement
  if (!hasCommercialAccess(hasModule, requiredModule, anyOfModules)) {
    if (b2bOnlyUser) {
      return <NotFoundPage />;
    }
    return <Navigate to="/upgrade" replace />;
  }

  // STEP 3 — Security (permission keys, then role allow-list)
  if (requiredPermission?.length && !requiredPermission.every((key) => hasPermission(key))) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (roles && !rolesInclude(sessionRoles, ...roles)) {
    if (b2bOnlyUser) {
      return <Navigate to="/showroom/catalog" replace />;
    }
    return <Navigate to={pickerOnly ? '/fulfillment' : '/dashboard'} replace />;
  }

  // STEP 4 — Success
  return children ? <>{children}</> : <Outlet />;
}
