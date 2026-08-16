import { Navigate, useLocation } from 'react-router-dom';
import {
  useSessionHydrated,
  useIsAuthenticated,
  useSessionRoles,
  rolesInclude,
  isExclusiveRole,
} from '@/stores/session';

interface ProtectedRouteProps {
  children: React.ReactNode;
  roles?: string[];
  /** Block B2B-only portal users from office routes */
  officeOnly?: boolean;
  /** Restrict to B2B portal users only */
  b2bOnly?: boolean;
}

/**
 * Layout gate reads frozen role snapshots from the store selector.
 * Does not call replaceable store methods or mutate role arrays locally.
 */
export function ProtectedRoute({
  children,
  roles,
  officeOnly = false,
  b2bOnly = false,
}: ProtectedRouteProps) {
  const hydrated = useSessionHydrated();
  const authenticated = useIsAuthenticated();
  const sessionRoles = useSessionRoles();
  const location = useLocation();

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface text-sm text-text-muted">
        Loading session…
      </div>
    );
  }

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

  if (roles && !rolesInclude(sessionRoles, ...roles)) {
    if (b2bOnlyUser) {
      return <Navigate to="/showroom/catalog" replace />;
    }
    return <Navigate to={pickerOnly ? '/fulfillment' : '/dashboard'} replace />;
  }

  return <>{children}</>;
}
