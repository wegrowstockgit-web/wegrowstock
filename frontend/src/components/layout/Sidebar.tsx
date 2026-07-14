import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Boxes,
  ClipboardList,
  Factory,
  FileBarChart,
  FileText,
  LayoutDashboard,
  Layers,
  Package,
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
    to: '/cycle-counts',
    label: 'Cycle counts',
    icon: ClipboardList,
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
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
  'transition-[width,padding,gap] duration-[var(--rail-duration)] ease-[var(--rail-ease)]';

/**
 * Expandable icon-rail: hover/focus peeks labels; pin locks expanded width
 * and pushes main content. Ctrl/⌘K remains the primary route switcher.
 */
export function Sidebar() {
  const hasRole = useSessionStore((s) => s.hasRole);
  const isPickerOnly = useSessionStore((s) => s.isPickerOnly);
  const isViewerOnly = useSessionStore((s) => s.isViewerOnly);
  const pinned = useRailStore((s) => s.pinned);
  const setPinned = useRailStore((s) => s.setPinned);

  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  /** After unpin, ignore hover/focus until the pointer leaves so collapse feels immediate. */
  const [peekLocked, setPeekLocked] = useState(false);

  const peeking = !peekLocked && (hovered || focused);
  const expanded = pinned || peeking;

  useEffect(() => {
    document.documentElement.style.setProperty(
      '--rail-width',
      pinned ? 'var(--rail-width-expanded)' : 'var(--rail-width-collapsed)'
    );
  }, [pinned]);

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
      'group relative flex h-10 shrink-0 items-center rounded-xl',
      'transition-[width,background-color,color,padding,transform] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
      'motion-safe:active:scale-[0.97]',
      expanded ? 'w-full px-3 gap-3' : 'w-10 justify-center px-0',
      isActive
        ? 'bg-accent-muted text-accent'
        : 'text-text-muted hover:bg-surface-overlay hover:text-text'
    );

  return (
    <aside
      data-testid="icon-rail"
      data-expanded={expanded ? 'true' : 'false'}
      data-pinned={pinned ? 'true' : 'false'}
      onMouseEnter={() => {
        if (!peekLocked) setHovered(true);
      }}
      onMouseLeave={() => {
        setHovered(false);
        setPeekLocked(false);
      }}
      onFocusCapture={() => {
        if (!peekLocked) setFocused(true);
      }}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setFocused(false);
        }
      }}
      className={cn(
        'pointer-events-none fixed inset-y-0 left-0 z-40 flex flex-col items-stretch py-3 pl-3',
        railTransition,
        expanded ? 'w-[var(--rail-width-expanded)]' : 'w-[var(--rail-width-collapsed)]',
        !pinned && expanded && 'z-50'
      )}
    >
      <div
        className={cn(
          'pointer-events-auto flex h-full flex-col gap-1 rounded-2xl',
          'border border-border/80 bg-surface-raised/95 py-3 shadow-elevated backdrop-blur-md',
          'supports-[backdrop-filter]:bg-surface-raised/80',
          railTransition,
          expanded ? 'w-[calc(var(--rail-width-expanded)-0.75rem)] px-2' : 'w-14 items-center px-0'
        )}
      >
        <div
          className={cn(
            'relative mb-1 flex w-full items-center',
            railTransition,
            expanded ? 'justify-between gap-2 px-1' : 'justify-center'
          )}
        >
          <div className={cn('flex min-w-0 items-center', expanded ? 'gap-2.5' : 'justify-center')}>
            <div
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent text-text-inverse shadow-card"
              title="InventorySystem"
              aria-hidden
            >
              <Boxes className="h-4 w-4" />
            </div>
            <span
              aria-hidden={!expanded}
              className={cn(
                'truncate text-sm font-semibold tracking-tight text-text',
                'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                expanded
                  ? 'max-w-[9rem] translate-x-0 opacity-100 delay-75'
                  : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0'
              )}
            >
              InventorySystem
            </span>
          </div>

          <button
            type="button"
            aria-pressed={pinned}
            aria-label={pinned ? 'Unpin navigation' : 'Pin navigation open'}
            title={pinned ? 'Unpin' : 'Pin open'}
            onMouseDown={(event) => event.preventDefault()}
            onClick={handlePinToggle}
            className={cn(
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg',
              'text-text-muted transition-[background-color,color,transform,opacity] duration-150 ease-out',
              'hover:bg-surface-overlay hover:text-text motion-safe:active:scale-[0.96]',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
              pinned && 'bg-accent-muted text-accent',
              expanded
                ? 'relative opacity-100'
                : 'pointer-events-none absolute right-0 top-0 opacity-0'
            )}
          >
            <Pin
              className={cn(
                'h-3.5 w-3.5 transition-transform duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                pinned ? 'rotate-0 fill-current' : 'rotate-45'
              )}
            />
          </button>
        </div>

        <nav
          className={cn(
            'flex flex-1 flex-col gap-0.5 overflow-y-auto overflow-x-hidden',
            railTransition,
            expanded ? 'items-stretch px-0' : 'items-center px-1'
          )}
          aria-label="Primary"
        >
          {visibleItems.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} title={label} aria-label={label} className={linkClass}>
              <Icon className="h-4 w-4 shrink-0" />
              <span
                className={cn(
                  'truncate text-sm font-medium',
                  'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                  expanded
                    ? 'max-w-[10rem] translate-x-0 opacity-100 delay-75'
                    : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0'
                )}
              >
                {label}
              </span>
            </NavLink>
          ))}
        </nav>

        {hasRole('ADMIN', 'OWNER') && (
          <NavLink to="/settings" title="Settings" aria-label="Settings" className={linkClass}>
            <Settings className="h-4 w-4 shrink-0" />
            <span
              className={cn(
                'truncate text-sm font-medium',
                'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                expanded
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
  );
}
