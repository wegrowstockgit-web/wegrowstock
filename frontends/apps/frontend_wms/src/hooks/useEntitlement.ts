import { useCallback } from 'react';
import { useEnabledModules, useSessionStore } from '@/stores/session';

/**
 * Commercial-tier entitlements for the signed-in tenant.
 * `activeModules` is the session `enabledModules` list from `/me`.
 */
export function useEntitlement() {
  const activeModules = useEnabledModules();
  const storeHasModule = useSessionStore((s) => s.hasModule);

  const hasModule = useCallback(
    (moduleName: string): boolean => storeHasModule(moduleName),
    [storeHasModule],
  );

  return { activeModules, hasModule };
}
