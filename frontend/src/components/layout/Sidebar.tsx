import { useCallback, useEffect, useRef, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  AlertTriangle,
  Boxes,
  ChevronDown,
  ChevronUp,
  ClipboardList,
  Factory,
  FileBarChart,
  FileText,
  FileUp,
  GitBranch,
  LayoutDashboard,
  Layers,
  Package,
  PackageCheck,
  Pin,
  RotateCcw,
  ScanLine,
  Settings,
  ShoppingCart,
  Truck,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useSessionStore } from '@/stores/session';
import { useRailStore } from '@/stores/rail';
import { useCoarsePointer } from '@/hooks/useCoarsePointer';
import { useMediaQuery } from '@/hooks/useMediaQuery';

type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  roles?: string[];
  hideForPicker?: boolean;
  hideForViewer?: boolean;
};

const navItems: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/products', label: 'Products', icon: Package },
  {
    to: '/fulfillment',
    label: 'Fulfillment',
    icon: ScanLine,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    to: '/exceptions',
    label: 'Exceptions',
    icon: AlertTriangle,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    to: '/import',
    label: 'Import',
    icon: FileUp,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    to: '/cycle-counts',
    label: 'Cycle counts',
    icon: ClipboardList,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    to: '/issue-supplies',
    label: 'Issue Supplies',
    icon: PackageCheck,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    to: '/field/truck',
    label: 'Technician Truck',
    icon: Truck,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    to: '/compliance/lot-trace',
    label: 'Lot Trace',
    icon: GitBranch,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER'],
  },
  {
    to: '/manufacturing/boms',
    label: 'Manufacturing',
    icon: Layers,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    to: '/manufacturing/orders',
    label: 'Production Orders',
    icon: Factory,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    to: '/returns',
    label: 'Returns',
    icon: RotateCcw,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  { to: '/purchase-orders', label: 'Purchase Orders', icon: ClipboardList, hideForPicker: true },
  { to: '/sales-orders', label: 'Sales Orders', icon: ShoppingCart, hideForPicker: true },
  { to: '/invoices', label: 'Invoices', icon: FileText, hideForPicker: true },
  {
    to: '/reports',
    label: 'Reports',
    icon: FileBarChart,
    roles: ['OWNER', 'ADMIN'],
    hideForPicker: true,
    hideForViewer: true,
  },
  { to: '/customers', label: 'Customers', icon: Users, hideForPicker: true },
  { to: '/suppliers', label: 'Suppliers', icon: Truck, hideForPicker: true },
];

const railTransition =
  'transition-[width,padding,gap,transform] duration-[var(--rail-duration)] ease-[var(--rail-ease)]';

/**
 * Expandable icon-rail: hover/focus peeks labels on fine pointers; pin locks
 * expanded width. Below 768px this becomes an overlay drawer. Ctrl/⌘K remains
 * the primary route switcher.
 */
export function Sidebar() {
  const location = useLocation();
  const hasRole = useSessionStore((s) => s.hasRole);
  const isPickerOnly = useSessionStore((s) => s.isPickerOnly);
  const isViewerOnly = useSessionStore((s) => s.isViewerOnly);
  const pinned = useRailStore((s) => s.pinned);
  const setPinned = useRailStore((s) => s.setPinned);
  const mobileOpen = useRailStore((s) => s.mobileOpen);
  const setMobileOpen = useRailStore((s) => s.setMobileOpen);
  const canScrollUp = useRailStore((s) => s.canScrollUp);
  const canScrollDown = useRailStore((s) => s.canScrollDown);
  const setScrollFold = useRailStore((s) => s.setScrollFold);

  const coarsePointer = useCoarsePointer();
  // iPad / tablet footprints (incl. portrait iPad) collapse the rail into a tap drawer.
  const isTabletOrBelow = useMediaQuery('(max-width: 1023px)');

  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  /** After unpin, ignore hover/focus until the pointer leaves so collapse feels immediate. */
  const [peekLocked, setPeekLocked] = useState(false);

  const navRef = useRef<HTMLElement>(null);

  const updateScrollFold = useCallback(() => {
    const el = navRef.current;
    if (!el) {
      setScrollFold(false, false);
      return;
    }
    const { scrollTop, scrollHeight, clientHeight } = el;
    const overflow = scrollHeight > clientHeight + 1;
    setScrollFold(overflow && scrollTop > 4, overflow && scrollTop + clientHeight < scrollHeight - 4);
  }, [setScrollFold]);

  useEffect(() => {
    const el = navRef.current;
    if (!el) return;
    updateScrollFold();
    el.addEventListener('scroll', updateScrollFold, { passive: true });
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(updateScrollFold) : null;
    ro?.observe(el);
    return () => {
      el.removeEventListener('scroll', updateScrollFold);
      ro?.disconnect();
    };
  }, [updateScrollFold, mobileOpen, pinned]);

  useEffect(() => {
    if (!isTabletOrBelow) {
      setMobileOpen(false);
    }
  }, [isTabletOrBelow, setMobileOpen]);

  useEffect(() => {
    if (isTabletOrBelow) setMobileOpen(false);
  }, [location.pathname, isTabletOrBelow, setMobileOpen]);

  // Hover peek only on fine pointers / desktop; touch uses pin or drawer.
  const peeking = !isTabletOrBelow && !coarsePointer && !peekLocked && (hovered || focused);
  const expanded = isTabletOrBelow ? true : pinned || peeking;

  useEffect(() => {
    if (isTabletOrBelow) {
      document.documentElement.style.setProperty('--rail-width', '0px');
      return;
    }
    // Keep main content padding in sync with peek/pin width so the flyout
    // cannot intercept clicks on page chrome (settings subnav, etc.).
    document.documentElement.style.setProperty(
      '--rail-width',
      expanded ? 'var(--rail-width-expanded)' : 'var(--rail-width-collapsed)'
    );
  }, [expanded, isTabletOrBelow]);

  const handlePinToggle = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();

    if (pinned) {
      setPinned(false);
      setPeekLocked(true);
      setHovered(false);
      setFocused(false);
      event.currentTarget.blur();
      return;
    }

    setPeekLocked(false);
    setPinned(true);
  };

  const visibleItems = navItems.filter((item) => {
    if (item.roles && !hasRole(...item.roles)) return false;
    if (isPickerOnly() && item.hideForPicker) return false;
    if (isViewerOnly() && item.hideForViewer) return false;
    return true;
  });

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'group relative flex min-h-11 shrink-0 items-center rounded-xl touch-target',
      'transition-[width,background-color,color,padding,transform] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
      'motion-safe:active:scale-[0.97]',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
      expanded ? 'w-full px-3 gap-3' : 'w-11 justify-center px-0',
      isActive
        ? 'bg-accent-muted text-accent'
        : 'text-text-muted hover:bg-surface-overlay hover:text-text'
    );

  const showOverlay = isTabletOrBelow;
  const railVisible = !showOverlay || mobileOpen;
  const hasOverflowMask = canScrollUp || canScrollDown;

  return (
    <>
      {showOverlay && mobileOpen && (
        <button
          type="button"
          aria-label="Close navigation"
          className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-[1px] lg:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <aside
        data-testid="icon-rail"
        data-expanded={expanded ? 'true' : 'false'}
        data-pinned={pinned ? 'true' : 'false'}
        data-mobile-open={mobileOpen ? 'true' : 'false'}
        onMouseEnter={() => {
          if (!peekLocked && !coarsePointer && !isTabletOrBelow) setHovered(true);
        }}
        onMouseLeave={() => {
          setHovered(false);
          setPeekLocked(false);
        }}
        onFocusCapture={() => {
          if (!peekLocked && !coarsePointer && !isTabletOrBelow) setFocused(true);
        }}
        onBlurCapture={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            setFocused(false);
          }
        }}
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col items-stretch py-3 pl-3',
          railTransition,
          showOverlay
            ? cn(
                'w-[min(18rem,calc(100vw-2rem))] pointer-events-auto',
                railVisible ? 'translate-x-0' : '-translate-x-full pointer-events-none'
              )
            : cn(
                'pointer-events-none',
                expanded ? 'w-[var(--rail-width-expanded)]' : 'w-[var(--rail-width-collapsed)]',
                !pinned && expanded && 'z-50'
              )
        )}
      >
        <div
          className={cn(
            'pointer-events-auto flex h-full flex-col gap-1 rounded-2xl',
            'border border-border/80 bg-surface-raised/95 py-3 shadow-elevated backdrop-blur-md',
            'supports-[backdrop-filter]:bg-surface-raised/80',
            railTransition,
            showOverlay || expanded
              ? 'w-[calc(100%-0rem)] px-2'
              : 'w-14 items-center px-0',
            !showOverlay && expanded && 'w-[calc(var(--rail-width-expanded)-0.75rem)]'
          )}
        >
          <div
            className={cn(
              'relative mb-1 flex w-full items-center',
              railTransition,
              expanded || showOverlay ? 'justify-between gap-2 px-1' : 'justify-center'
            )}
          >
            <div
              className={cn(
                'flex min-w-0 items-center',
                expanded || showOverlay ? 'gap-2.5' : 'justify-center'
              )}
            >
              <div
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-accent text-text-inverse shadow-card"
                title="InventorySystem"
                aria-hidden
              >
                <Boxes className="h-4 w-4" />
              </div>
              <span
                aria-hidden={!expanded && !showOverlay}
                className={cn(
                  'truncate text-sm font-semibold tracking-tight text-text',
                  'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                  expanded || showOverlay
                    ? 'max-w-[9rem] translate-x-0 opacity-100 delay-75'
                    : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0'
                )}
              >
                InventorySystem
              </span>
            </div>

            {!showOverlay && expanded && (
              <button
                type="button"
                aria-pressed={pinned}
                aria-label={pinned ? 'Unpin navigation' : 'Pin navigation open'}
                title={coarsePointer ? undefined : pinned ? 'Unpin' : 'Pin open'}
                onMouseDown={(event) => event.preventDefault()}
                onClick={handlePinToggle}
                className={cn(
                  'relative flex h-11 w-11 shrink-0 items-center justify-center rounded-lg touch-target',
                  'text-text-muted transition-[background-color,color,transform] duration-150 ease-out',
                  'hover:bg-surface-overlay hover:text-text motion-safe:active:scale-[0.96]',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
                  pinned && 'bg-accent-muted text-accent'
                )}
              >
                <Pin
                  className={cn(
                    'h-3.5 w-3.5 transition-transform duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                    pinned ? 'rotate-0 fill-current' : 'rotate-45'
                  )}
                />
              </button>
            )}
          </div>

          {/* Coarse pointer: expand labels via pin control in the icon stack (no hover peek). */}
          {!showOverlay && coarsePointer && !expanded && (
            <button
              type="button"
              aria-label="Expand navigation labels"
              onClick={() => {
                setPeekLocked(false);
                setPinned(true);
              }}
              className={cn(
                'flex h-11 w-11 shrink-0 items-center justify-center rounded-xl touch-target',
                'text-text-muted hover:bg-surface-overlay hover:text-text',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
                'motion-safe:active:scale-[0.97]'
              )}
            >
              <Pin className="h-3.5 w-3.5 rotate-45" />
            </button>
          )}

          <div className="relative min-h-0 flex-1">
            {canScrollUp && (
              <div
                className="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center pt-0.5"
                aria-hidden
              >
                <ChevronUp className="h-3.5 w-3.5 text-text-muted/80" />
              </div>
            )}
            {canScrollDown && (
              <div
                className="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex justify-center pb-0.5"
                aria-hidden
              >
                <ChevronDown className="h-3.5 w-3.5 text-text-muted/80" />
              </div>
            )}

            <nav
              ref={navRef}
              className={cn(
                'flex h-full flex-col gap-0.5 overflow-y-auto overflow-x-hidden scrollbar-none',
                hasOverflowMask && 'rail-scroll-mask',
                railTransition,
                expanded || showOverlay ? 'items-stretch px-0' : 'items-center px-1'
              )}
              aria-label="Primary"
            >
              {visibleItems.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  title={coarsePointer || showOverlay ? undefined : label}
                  aria-label={label}
                  className={linkClass}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span
                    className={cn(
                      'truncate text-sm font-medium',
                      'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                      expanded || showOverlay
                        ? 'max-w-[10rem] translate-x-0 opacity-100 delay-75'
                        : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0'
                    )}
                  >
                    {label}
                  </span>
                </NavLink>
              ))}
            </nav>
          </div>

          <NavLink
            to="/settings/profile"
            title={coarsePointer ? undefined : 'Personal settings'}
            aria-label="Personal settings"
            className={linkClass}
            data-testid="nav-personal-profile"
          >
            <Users className="h-4 w-4 shrink-0" />
            <span
              className={cn(
                'truncate text-sm font-medium',
                'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                expanded || showOverlay
                  ? 'max-w-[10rem] translate-x-0 opacity-100 delay-75'
                  : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0'
              )}
            >
              Profile
            </span>
          </NavLink>
          {hasRole('ADMIN', 'OWNER') && (
            <NavLink to="/settings" title={coarsePointer ? undefined : 'Settings'} aria-label="Settings" className={linkClass}>
              <Settings className="h-4 w-4 shrink-0" />
              <span
                className={cn(
                  'truncate text-sm font-medium',
                  'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                  expanded || showOverlay
                    ? 'max-w-[10rem] translate-x-0 opacity-100 delay-75'
                    : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0'
                )}
              >
                Settings
              </span>
            </NavLink>
          )}
        </div>
      </aside>
    </>
  );
}
