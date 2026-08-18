import type { ReactNode } from 'react';
import { useEntitlement } from '@/hooks/useEntitlement';

export type RequireModuleProps = {
  required?: string;
  /** Entitled if the tenant owns any of these commercial modules. */
  anyOf?: readonly string[];
  fallback?: ReactNode;
  children: ReactNode;
};

/**
 * UI wrapper: hide premium chrome when the tenant has not purchased the module.
 */
export function RequireModule({
  required,
  anyOf,
  fallback = null,
  children,
}: RequireModuleProps) {
  const { hasModule } = useEntitlement();
  const allowed = required
    ? hasModule(required)
    : anyOf?.length
      ? anyOf.some((moduleName) => hasModule(moduleName))
      : true;

  if (!allowed) return <>{fallback}</>;
  return <>{children}</>;
}
