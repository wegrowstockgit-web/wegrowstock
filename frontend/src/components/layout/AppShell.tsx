import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, Search, Warehouse } from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Sidebar } from './Sidebar';
import { CommandPalette, useCommandPalette } from './CommandPalette';
import { Button } from '@/components/ui/Button';
import { SyncConflictToast } from '@/components/ui/SyncConflictToast';
import { useSessionStore, useIsAuthenticated } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { apiClient } from '@/api/client';
import { signOut } from '@/lib/signOut';
import type { Warehouse as WarehouseType } from '@/api/types';
import { cn } from '@/lib/utils';

function isWarehouseRoute(pathname: string): boolean {
  return (
    pathname.startsWith('/fulfillment') ||
    pathname.startsWith('/cycle-counts') ||
    pathname.startsWith('/manufacturing/terminal') ||
    pathname.startsWith('/returns/receive')
  );
}

export function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { open, close, toggle } = useCommandPalette();
  const authenticated = useIsAuthenticated();
  const user = useSessionStore((s) => s.user);
  const { warehouse, setWarehouse } = useActiveWarehouseStore();

  const isWarehouseView = isWarehouseRoute(location.pathname);

  useEffect(() => {
    document.documentElement.setAttribute(
      'data-theme',
      isWarehouseView ? 'warehouse' : 'office'
    );
  }, [isWarehouseView]);

  const { data: warehouses = [] } = useQuery({
    queryKey: ['warehouses'],
    queryFn: async () => {
      const res = await apiClient.get<WarehouseType[]>('/api/v1/locations?type=WAREHOUSE');
      return res.data;
    },
    enabled: authenticated,
    retry: false,
  });

  useEffect(() => {
    if (warehouses.length === 0) return;
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
  }, [warehouse, warehouses, setWarehouse]);

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  const handleWarehouseChange = (warehouseId: string) => {
    const selected = warehouses.find((item) => item.id === warehouseId);
    if (!selected || selected.id === warehouse?.id) return;
    setWarehouse(selected);
    void queryClient.invalidateQueries({ queryKey: ['picking'] });
    void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
  };

  return (
    <div className="relative flex h-screen overflow-hidden bg-surface">
      {!isWarehouseView && <Sidebar />}

      <div
        className={cn(
          'flex min-w-0 flex-1 flex-col overflow-hidden',
          !isWarehouseView &&
            'pl-[var(--rail-width)] transition-[padding] duration-[var(--rail-duration)] ease-[var(--rail-ease)]'
        )}
      >
        <header
          className={cn(
            'flex h-[var(--header-height)] shrink-0 items-center justify-between px-4',
            'border-b border-border/60 bg-surface-raised/80 backdrop-blur-md',
            isWarehouseView && 'border-border/60'
          )}
        >
          <div className="flex items-center gap-3">
            {!isWarehouseView && (
              <Button
                variant="secondary"
                size="sm"
                onClick={toggle}
                className="hidden sm:inline-flex"
              >
                <Search className="h-4 w-4" />
                <span className="text-text-muted">Search</span>
                <kbd className="ml-2 rounded border border-border px-1 text-xs">⌘K</kbd>
              </Button>
            )}

            {isWarehouseView && (
              <p className="text-sm font-semibold tracking-wide text-text">Floor ops</p>
            )}

            {warehouses.length > 0 && (
              <div className="relative" title="Active warehouse for fulfillment and scanning">
                <select
                  value={warehouse?.id ?? ''}
                  onChange={(e) => handleWarehouseChange(e.target.value)}
                  aria-label="Active warehouse"
                  className={cn(
                    'h-9 appearance-none rounded-md border border-border bg-surface-raised pl-9 pr-8 text-sm text-text',
                    isWarehouseView && 'h-11 min-w-[10rem] text-base'
                  )}
                >
                  {warehouses.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
                <Warehouse className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
                <ChevronDown className="pointer-events-none absolute right-2 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              </div>
            )}
          </div>

          <div className="flex items-center gap-3">
            {!isWarehouseView && (
              <span className="hidden text-sm text-text-muted sm:inline">
                {user?.displayName ?? user?.email}
              </span>
            )}
            <Button variant="ghost" size="sm" onClick={() => void handleSignOut()}>
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sign out</span>
            </Button>
          </div>
        </header>

        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>

      {!isWarehouseView && <CommandPalette open={open} onClose={close} />}
      <SyncConflictToast />
    </div>
  );
}
