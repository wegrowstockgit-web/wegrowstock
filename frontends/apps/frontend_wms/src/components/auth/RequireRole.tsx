import type { ReactNode } from 'react';
import { useSessionStore } from '@/stores/session';

export type RequireRoleProps = {
  roles: readonly string[];
  fallback?: ReactNode;
  children: ReactNode;
};

/**
 * UI wrapper: hide privileged chrome unless the signed-in user holds one of the roles.
 */
export function RequireRole({ roles, fallback = null, children }: RequireRoleProps) {
  const hasRole = useSessionStore((s) => s.hasRole);
  if (!hasRole(...roles)) return <>{fallback}</>;
  return <>{children}</>;
}
