import { Navigate, useLocation } from 'react-router-dom';
import { useSessionStore, useSessionHydrated, useIsAuthenticated } from '@/stores/session';

interface ProtectedRouteProps {
  children: React.ReactNode;
  roles?: string[];
  /** Block B2B-only portal users from office routes */
  officeOnly?: boolean;
  /** Restrict to B2B portal users only */
  b2bOnly?: boolean;
}

export function ProtectedRoute({
  children,
  roles,
  officeOnly = false,
  b2bOnly = false,
}: ProtectedRouteProps) {
  const hydrated = useSessionHydrated();
  const authenticated = useIsAuthenticated();
  const hasRole = useSessionStore((s) => s.hasRole);
  const isB2bCustomerOnly = useSessionStore((s) => s.isB2bCustomerOnly);
  const location = useLocation();

  if (!hydrated) {
    return null;
  }

  if (!authenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (b2bOnly && !hasRole('B2B_CUSTOMER')) {
    return <Navigate to="/dashboard" replace />;
  }

  if (officeOnly && isB2bCustomerOnly()) {
    return <Navigate to="/showroom/catalog" replace />;
  }

  if (roles && !hasRole(...roles)) {
    if (isB2bCustomerOnly()) {
      return <Navigate to="/showroom/catalog" replace />;
    }
    const isPickerOnly = useSessionStore.getState().isPickerOnly();
    return <Navigate to={isPickerOnly ? '/fulfillment' : '/dashboard'} replace />;
  }

  return <>{children}</>;
}
