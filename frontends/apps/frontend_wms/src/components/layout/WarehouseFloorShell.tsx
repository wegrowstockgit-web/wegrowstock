import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Header } from './Header';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { SyncConflictToast } from '@/components/ui/SyncConflictToast';
import { useSessionStore, useIsAuthenticated, useSessionWarehouseIds } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useWarehouseStore } from '@/stores/warehouseStore';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useWarehouseContextGate } from '@/hooks/useWarehouseContextGate';
import { apiClient } from '@/api/client';
import { signOut } from '@/lib/signOut';

interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  grantedPermissions?: string[];
  isSuperAdmin?: boolean;
  enabledModules?: string[];
  localeLanguage?: string | null;
  tier?: string | null;
}

/**
 * Floor-ops viewport: warehouse theme + header only — no corporate Sidebar / command palette.
 */
export function WarehouseFloorShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const authenticated = useIsAuthenticated();
  const applyMeProfile = useSessionStore((s) => s.applyMeProfile);
  const sessionWarehouseIds = useSessionWarehouseIds();
  const warehouse = useActiveWarehouseStore((s) => s.warehouse);
  const setWarehouse = useActiveWarehouseStore((s) => s.setWarehouse);
  const lockFromJwtSingle = useActiveWarehouseStore((s) => s.lockFromJwtSingle);
  const contextLocked = useActiveWarehouseStore((s) => s.contextLocked);
  const lockReason = useActiveWarehouseStore((s) => s.lockReason);
  const fetchAllowedWarehouses = useWarehouseStore((s) => s.fetchAllowed);
  const switcherDisabled = useWarehouseStore((s) => s.switcherDisabled);
  const densityMode = usePreferencesStore((s) => s.densityMode);

  const jwtTerminalLocked = sessionWarehouseIds.length === 1;
  const hideSwitcher = jwtTerminalLocked || contextLocked || switcherDisabled;

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', 'warehouse');
    return () => {
      document.documentElement.setAttribute('data-theme', 'office');
    };
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute('data-density', densityMode);
  }, [densityMode]);

  useQuery({
    queryKey: ['auth', 'me'],
    queryFn: async () => {
      const { data } = await apiClient.get<MeResponse>('/api/v1/auth/me');
        applyMeProfile({
          userId: data.userId,
          email: data.email,
          displayName: data.displayName,
          roles: data.roles,
          warehouseIds: data.warehouseIds,
          avatarUrl: data.avatarUrl,
          tenantId: data.tenantId,
          grantedPermissions: data.grantedPermissions,
          isSuperAdmin: data.isSuperAdmin,
          enabledModules: data.enabledModules,
          localeLanguage: data.localeLanguage,
          tier: data.tier,
        });
      return data;
    },
    enabled: authenticated,
    retry: false,
    staleTime: 60_000,
  });

  const { data: warehouses = [] } = useQuery({
    queryKey: ['warehouses', 'allowed'],
    queryFn: () => fetchAllowedWarehouses(),
    enabled: authenticated,
    retry: false,
  });

  useWarehouseContextGate(warehouses, jwtTerminalLocked);

  useEffect(() => {
    if (warehouses.length === 0) return;

    if (jwtTerminalLocked) {
      const lockedId = sessionWarehouseIds[0];
      const locked = warehouses.find((item) => item.id === lockedId) ?? warehouses[0];
      lockFromJwtSingle(locked);
      return;
    }

    if (contextLocked && warehouse) {
      const stillValid = warehouses.find((item) => item.id === warehouse.id);
      if (stillValid && (stillValid.name !== warehouse.name || stillValid.code !== warehouse.code)) {
        setWarehouse(stillValid, { force: true, lockReason: lockReason ?? undefined });
      }
      return;
    }

    const matched = warehouse?.id
      ? warehouses.find((item) => item.id === warehouse.id)
      : null;
    if (matched) {
      if (matched.name !== warehouse?.name || matched.code !== warehouse?.code) {
        setWarehouse(matched);
      }
      return;
    }
    setWarehouse(warehouses[0]);
  }, [
    warehouse,
    warehouses,
    setWarehouse,
    jwtTerminalLocked,
    sessionWarehouseIds,
    lockFromJwtSingle,
    contextLocked,
    lockReason,
  ]);

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  const handleWarehouseChange = (warehouseId: string) => {
    if (hideSwitcher) return;
    const selected = warehouses.find((item) => item.id === warehouseId);
    if (!selected || selected.id === warehouse?.id) return;
    setWarehouse(selected);
    void queryClient.invalidateQueries({ queryKey: ['picking'] });
    void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
  };

  const lockTitle =
    lockReason === 'HARDWARE_SSID'
      ? 'Warehouse locked by Wi-Fi SSID'
      : lockReason === 'HARDWARE_GEOFENCE'
        ? 'Warehouse locked by geofence'
        : 'Warehouse locked by terminal assignment';

  return (
    <div
      className="relative flex h-screen overflow-hidden bg-surface"
      data-testid="warehouse-floor-shell"
      data-theme="warehouse"
    >
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <Header
          title="Floor ops"
          isWarehouseView
          warehouses={warehouses}
          warehouse={warehouse}
          hideSwitcher={hideSwitcher}
          lockTitle={lockTitle}
          lockReason={lockReason}
          onToggleCommandPalette={() => undefined}
          onWarehouseChange={handleWarehouseChange}
          onSignOut={() => void handleSignOut()}
        />

        <main className="flex min-h-0 flex-1 flex-col overflow-y-auto overscroll-contain">
          <ErrorBoundary
            boundaryName={`floor:${location.pathname}`}
            className="flex min-h-0 flex-1 flex-col"
          >
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>

      <SyncConflictToast />
    </div>
  );
}
