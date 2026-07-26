import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { CommandPalette, useCommandPalette } from './CommandPalette';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { SyncConflictToast } from '@/components/ui/SyncConflictToast';
import { useSessionStore, useIsAuthenticated, useSessionWarehouseIds } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useWarehouseStore } from '@/stores/warehouseStore';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useWarehouseContextGate } from '@/hooks/useWarehouseContextGate';
import { apiClient } from '@/api/client';
import { signOut } from '@/lib/signOut';
import { cn } from '@/lib/utils';
import { ScrollFadePort } from '@/components/ui/ScrollFadePort';

/** Settings shells that own ScrollFadePort(s) — clip main so no outer scrollbar. */
function isSettingsOwnedScrollRoute(pathname: string): boolean {
  if (pathname === '/settings') return true;
  return (
    pathname.startsWith('/settings/profile') ||
    pathname.startsWith('/settings/billing') ||
    pathname.startsWith('/settings/integrations') ||
    pathname.startsWith('/settings/fintech') ||
    pathname.startsWith('/settings/users')
  );
}

/** Document pages that scroll in main with hidden bars + fold cues. */
function isMainFadeScrollRoute(pathname: string): boolean {
  return pathname === '/' || pathname === '/dashboard';
}

/** Virtualized grids own their scrollport — clip main so the page never pushes sideways. */
function isViewportLockedRoute(pathname: string): boolean {
  return (
    pathname === '/products' ||
    pathname.startsWith('/products/') ||
    pathname === '/purchase-orders' ||
    pathname === '/sales-orders' ||
    pathname === '/invoices' ||
    pathname === '/customers' ||
    pathname === '/suppliers'
  );
}

interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  grantedPermissions?: string[];
}

export function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { open, close, toggle } = useCommandPalette();
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

  const jwtTerminalLocked = sessionWarehouseIds.length === 1;
  const hideSwitcher = jwtTerminalLocked || contextLocked || switcherDisabled;

  const densityMode = usePreferencesStore((s) => s.densityMode);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', 'office');
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
      data-testid="app-shell"
    >
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden md:pl-[var(--rail-width)] transition-[padding] duration-[var(--rail-duration)] ease-[var(--rail-ease)]">
        <Header
          title=""
          isWarehouseView={false}
          warehouses={warehouses}
          warehouse={warehouse}
          hideSwitcher={hideSwitcher}
          lockTitle={lockTitle}
          lockReason={lockReason}
          onToggleCommandPalette={toggle}
          onWarehouseChange={handleWarehouseChange}
          onSignOut={() => void handleSignOut()}
        />

        {/*
          Document pages scroll here. Virtualized grid pages set a viewport-locked
          root (calc 100dvh − header) with overflow-hidden so only the table
          scrollport moves — the outer window never gains a second scrollbar.
          Settings / dashboard use ScrollFadePort (hidden bar + fold cues).
        */}
        <main
          className={cn(
            'flex min-h-0 min-w-0 flex-1 flex-col overscroll-contain',
            isSettingsOwnedScrollRoute(location.pathname) ||
              isMainFadeScrollRoute(location.pathname) ||
              isViewportLockedRoute(location.pathname)
              ? 'overflow-hidden'
              : 'overflow-y-auto',
          )}
        >
          {isMainFadeScrollRoute(location.pathname) ? (
            <ScrollFadePort
              data-testid="app-main-scroll"
              measureKey={location.pathname}
              shellClassName="min-h-0 min-w-0 flex-1"
              className="h-full overflow-y-auto overflow-x-hidden"
            >
              <ErrorBoundary
                boundaryName={`route:${location.pathname}`}
                className="flex min-h-0 min-w-0 flex-1 flex-col"
              >
                <Outlet />
              </ErrorBoundary>
            </ScrollFadePort>
          ) : (
            <ErrorBoundary
              boundaryName={`route:${location.pathname}`}
              className="flex min-h-0 min-w-0 flex-1 flex-col"
            >
              <Outlet />
            </ErrorBoundary>
          )}
        </main>
      </div>

      <CommandPalette open={open} onClose={close} />
      <SyncConflictToast />
    </div>
  );
}
