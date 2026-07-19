import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, ChevronDown, LogOut, Menu, Search, Settings2, User, Warehouse } from 'lucide-react';
import { Avatar, initialsFromName } from '@/components/ui/Avatar';
import { Button } from '@/components/ui/Button';
import { ProfileSettingsDialog } from '@/components/layout/ProfileSettingsDialog';
import { TerminalPinPad } from '@/components/layout/TerminalPinPad';
import { useSessionStore } from '@/stores/session';
import { useOfflineStore } from '@/stores/offlineStore';
import { useRailStore } from '@/stores/rail';
import { cn } from '@/lib/utils';
import type { Warehouse as WarehouseType } from '@/api/types';

interface HeaderProps {
  title: string;
  isWarehouseView: boolean;
  warehouses: WarehouseType[];
  warehouse: WarehouseType | null;
  hideSwitcher: boolean;
  lockTitle: string;
  lockReason: string | null;
  onToggleCommandPalette: () => void;
  onWarehouseChange: (warehouseId: string) => void;
  onSignOut: () => void;
  /** Optional Surface B quarantine review opener (preferred over navigate). */
  onOpenQuarantine?: () => void;
}

export function Header({
  title,
  isWarehouseView,
  warehouses,
  warehouse,
  hideSwitcher,
  lockTitle,
  lockReason,
  onToggleCommandPalette,
  onWarehouseChange,
  onSignOut,
  onOpenQuarantine,
}: HeaderProps) {
  const navigate = useNavigate();
  const user = useSessionStore((s) => s.user);
  const quarantineCount = useOfflineStore((s) => s.quarantinedMutations.length);
  const toggleMobileOpen = useRailStore((s) => s.toggleMobileOpen);
  const [menuOpen, setMenuOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const initials = initialsFromName(user?.displayName, user?.email);

  useEffect(() => {
    if (!menuOpen) return;
    const onDoc = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [menuOpen]);

  return (
    <>
      <header
        className={cn(
          // relative z-50: backdrop-blur creates a stacking context; without an
          // explicit z-index, later main content (e.g. dashboard CTAs) paints over
          // the account menu and blocks Sign out / Profile Settings.
          'relative z-50 flex h-[var(--header-height)] shrink-0 items-center justify-between border-b border-border/60 bg-surface-raised/80 px-4 backdrop-blur-md',
        )}
      >
        <div className="flex items-center gap-3">
          {!isWarehouseView && (
            <Button
              variant="secondary"
              size="sm"
              onClick={toggleMobileOpen}
              className="min-h-11 min-w-11 touch-target lg:hidden"
              aria-label="Open navigation"
            >
              <Menu className="h-5 w-5" />
            </Button>
          )}
          {!isWarehouseView && (
            <Button
              variant="secondary"
              size="sm"
              onClick={onToggleCommandPalette}
              className="hidden min-h-11 touch-target sm:inline-flex"
            >
              <Search className="h-4 w-4" />
              <span className="text-text-muted">Search</span>
              <kbd className="ml-2 rounded border border-border px-1 text-xs">⌘K</kbd>
            </Button>
          )}
          {title ? (
            <h1 className={cn('font-semibold text-text', isWarehouseView ? 'text-sm tracking-wide' : 'text-base')}>
              {title}
            </h1>
          ) : null}

          {warehouses.length > 0 &&
            (hideSwitcher ? (
              <div
                className="flex h-9 items-center gap-2 rounded-md border border-border bg-surface-overlay px-3 text-sm text-text"
                title={lockTitle}
                aria-label={`Locked warehouse ${warehouse?.name ?? ''}`.trim()}
                data-terminal-locked="true"
                data-lock-reason={lockReason ?? 'JWT_SINGLE'}
              >
                <Warehouse className="h-4 w-4 shrink-0 text-text-muted" />
                <span className="font-medium">{warehouse?.name ?? 'Warehouse'}</span>
              </div>
            ) : (
              <div className="relative" title="Active warehouse for fulfillment and scanning">
                <select
                  value={warehouse?.id ?? ''}
                  onChange={(e) => onWarehouseChange(e.target.value)}
                  aria-label="Active warehouse"
                  className={cn(
                    'h-9 appearance-none rounded-md border border-border bg-surface-raised pl-9 pr-8 text-sm text-text',
                    isWarehouseView && 'h-11 min-w-[10rem] text-base',
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
            ))}
        </div>

        <div className="flex items-center gap-3">
          {isWarehouseView && quarantineCount > 0 && (
            <button
              type="button"
              data-testid="quarantine-badge"
              className={cn(
                'inline-flex min-h-11 items-center gap-2 rounded-md border-2 border-danger',
                'bg-danger px-3 py-2 text-sm font-bold text-white shadow-sm',
              )}
              onClick={() => {
                if (onOpenQuarantine) onOpenQuarantine();
                else navigate('/fulfillment?quarantine=1');
              }}
              aria-label={`${quarantineCount} quarantined offline scans`}
            >
              <AlertTriangle className="h-4 w-4" aria-hidden />
              {quarantineCount} quarantine
            </button>
          )}
          {isWarehouseView && <TerminalPinPad warehouseSized />}
          <div
            className={cn('relative', menuOpen && 'z-[60]')}
            ref={menuRef}
            data-testid="header-user"
          >
            <button
              type="button"
              className="flex items-center gap-2 rounded-md px-1 py-0.5 hover:bg-surface-overlay"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              data-testid="header-user-trigger"
              onClick={() => setMenuOpen((o) => !o)}
            >
              <Avatar
                src={user?.avatarUrl}
                alt={user?.displayName ?? user?.email ?? 'User'}
                size={isWarehouseView ? 'lg' : 'md'}
                fallback={initials || <User className="h-4 w-4" aria-hidden />}
              />
              {!isWarehouseView && (
                <span className="hidden text-sm text-text-muted sm:inline">
                  {user?.displayName ?? user?.email}
                </span>
              )}
              <ChevronDown className="hidden h-3.5 w-3.5 text-text-muted sm:inline" />
            </button>
            {menuOpen && (
              <div
                role="menu"
                data-testid="header-user-menu"
                className="absolute right-0 z-[60] mt-2 w-52 rounded-md border border-border bg-surface-raised py-1 shadow-elevated"
              >
                <button
                  type="button"
                  role="menuitem"
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-text hover:bg-surface-overlay"
                  onClick={() => {
                    setMenuOpen(false);
                    setProfileOpen(true);
                  }}
                >
                  <Settings2 className="h-4 w-4 text-text-muted" />
                  Profile Settings
                </button>
                <button
                  type="button"
                  role="menuitem"
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-text hover:bg-surface-overlay"
                  onClick={() => {
                    setMenuOpen(false);
                    onSignOut();
                  }}
                >
                  <LogOut className="h-4 w-4 text-text-muted" />
                  Sign out
                </button>
              </div>
            )}
          </div>
          {isWarehouseView && (
            <Button variant="ghost" size="sm" onClick={onSignOut}>
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sign out</span>
            </Button>
          )}
        </div>
      </header>

      <ProfileSettingsDialog open={profileOpen} onClose={() => setProfileOpen(false)} />
    </>
  );
}
