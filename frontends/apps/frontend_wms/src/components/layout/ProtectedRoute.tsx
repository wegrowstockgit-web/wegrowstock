import type { ReactNode } from 'react';
import { EnterpriseRouteGate } from '@/components/auth/EnterpriseRouteGate';

interface ProtectedRouteProps {
  children: ReactNode;
  roles?: string[];
  officeOnly?: boolean;
  b2bOnly?: boolean;
}

/**
 * Compatibility wrapper around {@link EnterpriseRouteGate} for child-element routes.
 */
export function ProtectedRoute({
  children,
  roles,
  officeOnly = false,
  b2bOnly = false,
}: ProtectedRouteProps) {
  return (
    <EnterpriseRouteGate roles={roles} officeOnly={officeOnly} b2bOnly={b2bOnly}>
      {children}
    </EnterpriseRouteGate>
  );
}
