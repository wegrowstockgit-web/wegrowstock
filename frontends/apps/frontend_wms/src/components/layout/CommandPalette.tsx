import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSessionStore } from '@/stores/session';
import { globalSearch } from '@/api/globalSearch';
import {
  LayoutDashboard,
  Package,
  PackageCheck,
  PackageOpen,
  ScanLine,
  Settings,
  ShoppingCart,
  FileText,
  FileBarChart,
  Users,
  Truck,
  ClipboardList,
  Factory,
  Layers,
  RotateCcw,
  GitBranch,
  Search,
  Loader2,
  MapPin,
  Hash,
  DollarSign,
  Box,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface CommandItem {
  id: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  path: string;
  keywords?: string[];
  roles?: string[];
  hideForPicker?: boolean;
  hideForViewer?: boolean;
}

const commands: CommandItem[] = [
  { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, path: '/dashboard', keywords: ['home'] },
  { id: 'products', label: 'Products', icon: Package, path: '/products', keywords: ['sku', 'inventory', 'variants'] },
  {
    id: 'fulfillment',
    label: 'Fulfillment',
    icon: ScanLine,
    path: '/fulfillment',
    keywords: ['scan', 'pick', 'warehouse', 'ship'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    id: 'issue-supplies',
    label: 'Issue Supplies',
    icon: PackageCheck,
    path: '/issue-supplies',
    keywords: ['requisition', 'stockroom', 'issue'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    id: 'replenishments',
    label: 'Replenishments',
    icon: PackageOpen,
    path: '/replenishments',
    keywords: ['reserve', 'pick face', 'replenish', 'transfer'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    id: 'field-truck',
    label: 'Technician Truck',
    icon: Truck,
    path: '/field/truck',
    keywords: ['van', 'field', 'technician'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    id: 'lot-trace',
    label: 'Lot Trace',
    icon: GitBranch,
    path: '/compliance/lot-trace',
    keywords: ['genealogy', 'recall', 'compliance', 'lot'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER'],
  },
  {
    id: 'manufacturing-boms',
    label: 'Manufacturing BOMs',
    icon: Layers,
    path: '/manufacturing/boms',
    keywords: ['bom', 'bill of materials', 'production'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    id: 'manufacturing-orders',
    label: 'Manufacturing Orders',
    icon: Factory,
    path: '/manufacturing/orders',
    keywords: ['production', 'work order', 'manufacturing'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    id: 'returns',
    label: 'Returns',
    icon: RotateCcw,
    path: '/returns',
    keywords: ['rma', 'refund'],
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    id: 'returns-receive',
    label: 'Receive Returns',
    icon: RotateCcw,
    path: '/returns/receive',
    keywords: ['receive', 'rma', 'scan'],
    // Matches App route — exclusive pickers may deep-link / Ctrl+K from office shell.
    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
    hideForViewer: true,
  },
  {
    id: 'purchase-orders',
    label: 'Purchase Orders',
    icon: ClipboardList,
    path: '/purchase-orders',
    keywords: ['po', 'buy', 'procurement'],
    hideForPicker: true,
  },
  {
    id: 'sales-orders',
    label: 'Sales Orders',
    icon: ShoppingCart,
    path: '/sales-orders',
    keywords: ['so', 'sell', 'order'],
    hideForPicker: true,
  },
  {
    id: 'invoices',
    label: 'Invoices',
    icon: FileText,
    path: '/invoices',
    keywords: ['billing', 'payment'],
    hideForPicker: true,
  },
  {
    id: 'reports',
    label: 'Reports',
    icon: FileBarChart,
    path: '/reports',
    keywords: ['analytics', 'valuation', 'cogs'],
    roles: ['OWNER', 'ADMIN'],
    hideForPicker: true,
    hideForViewer: true,
  },
  {
    id: 'customers',
    label: 'Customers',
    icon: Users,
    path: '/customers',
    keywords: ['client', 'buyer'],
    hideForPicker: true,
  },
  {
    id: 'suppliers',
    label: 'Suppliers',
    icon: Truck,
    path: '/suppliers',
    keywords: ['vendor'],
    hideForPicker: true,
  },
  {
    id: 'settings',
    label: 'Settings',
    icon: Settings,
    path: '/settings',
    keywords: ['users', 'warehouse', 'integrations'],
    roles: ['ADMIN', 'OWNER'],
  },
  {
    id: 'integrations-hub',
    label: 'Integrations Hub',
    icon: Settings,
    path: '/settings/integrations',
    keywords: ['shopify', 'amazon', 'xero', 'quickbooks', 'netsuite', 'edi', 'as2'],
    roles: ['ADMIN', 'OWNER'],
  },
];

type PaletteItem =
  | { kind: 'command'; id: string; label: string; icon: React.ComponentType<{ className?: string }>; path: string }
  | {
      kind: 'result';
      id: string;
      label: string;
      sublabel?: string;
      category: string;
      path: string;
      icon: React.ComponentType<{ className?: string }>;
    };

function categoryIcon(category: string): LucideIcon {
  switch (category) {
    case 'Catalog':
      return Package;
    case 'Sales Order':
    case 'B2B Order':
      return ShoppingCart;
    case 'Purchase Order':
      return ClipboardList;
    case 'Customer':
      return Users;
    case 'Supplier':
      return Truck;
    case 'Invoice':
    case 'Factored Invoice':
      return DollarSign;
    case 'Lot':
      return GitBranch;
    case 'Serial':
      return Hash;
    case 'LPN':
      return Box;
    case 'Location':
    case 'Zone':
      return MapPin;
    default:
      return Search;
  }
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const hasRole = useSessionStore((s) => s.hasRole);
  const isPickerOnly = useSessionStore((s) => s.isPickerOnly);
  const isViewerOnly = useSessionStore((s) => s.isViewerOnly);
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [query, open]);

  const visibleCommands = useMemo(
    () =>
      commands.filter((cmd) => {
        if (cmd.roles && !hasRole(...cmd.roles)) return false;
        if (isPickerOnly() && cmd.hideForPicker) return false;
        if (isViewerOnly() && cmd.hideForViewer) return false;
        return true;
      }),
    [hasRole, isPickerOnly, isViewerOnly]
  );

  const filteredCommands = useMemo(() => {
    const q = query.toLowerCase().trim();
    if (!q) return visibleCommands;
    return visibleCommands.filter(
      (cmd) =>
        cmd.label.toLowerCase().includes(q) ||
        cmd.keywords?.some((keyword) => keyword.includes(q))
    );
  }, [query, visibleCommands]);

  const { data: searchResults = [], isFetching: isSearching } = useQuery({
    queryKey: ['global-search', debouncedQuery],
    queryFn: () => globalSearch(debouncedQuery),
    enabled: open && debouncedQuery.length >= 2,
    staleTime: 30_000,
    retry: false,
  });

  const items: PaletteItem[] = useMemo(() => {
    const commandItems: PaletteItem[] = filteredCommands.map((cmd) => ({
      kind: 'command',
      id: cmd.id,
      label: cmd.label,
      icon: cmd.icon,
      path: cmd.path,
    }));

    if (debouncedQuery.length < 2) {
      return commandItems;
    }

    const resultItems: PaletteItem[] = searchResults.map((result) => ({
      kind: 'result',
      id: result.id,
      label: result.title,
      sublabel: result.subtitle,
      category: result.category,
      path: result.route,
      icon: categoryIcon(result.category),
    }));

    return [...commandItems, ...resultItems];
  }, [filteredCommands, debouncedQuery, searchResults]);

  useEffect(() => {
    if (open) {
      setQuery('');
      setDebouncedQuery('');
      setSelectedIndex(0);
    }
  }, [open]);

  useEffect(() => {
    setSelectedIndex(0);
  }, [query, items.length]);

  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((index) => Math.min(index + 1, items.length - 1));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((index) => Math.max(index - 1, 0));
      } else if (e.key === 'Enter' && items[selectedIndex]) {
        e.preventDefault();
        navigate(items[selectedIndex].path);
        onClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, items, selectedIndex, navigate, onClose]);

  if (!open) return null;

  const showEntitySection = debouncedQuery.length >= 2;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 pt-[15vh]">
      <div
        className="w-full max-w-lg overflow-hidden rounded-xl border border-border bg-surface-raised shadow-elevated"
        role="dialog"
        aria-label="Command palette"
      >
        <div className="flex items-center gap-3 border-b border-border px-4">
          <Search className="h-4 w-4 text-text-muted" />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('search.placeholder')}
            className="h-12 flex-1 bg-transparent text-sm text-text outline-none placeholder:text-text-muted"
          />
          {isSearching && <Loader2 className="h-4 w-4 animate-spin text-text-muted" />}
          <kbd className="rounded border border-border px-1.5 py-0.5 text-xs text-text-muted">
            Esc
          </kbd>
        </div>

        <ul className="max-h-80 overflow-y-auto py-2">
          {items.length === 0 ? (
            <li className="px-4 py-6 text-center text-sm text-text-muted">
              {debouncedQuery.length >= 2 ? t('search.noMatches') : t('search.noCommands')}
            </li>
          ) : (
            items.map((item, index) => {
              const previous = items[index - 1];
              const showSectionHeader =
                item.kind === 'result' &&
                (index === 0 ||
                  previous?.kind !== 'result' ||
                  (previous.kind === 'result' && previous.category !== item.category));
              const showPagesHeader = item.kind === 'command' && index === 0;

              return (
                <li key={item.id}>
                  {showPagesHeader && (
                    <p className="px-4 pb-1 pt-2 text-xs font-medium uppercase tracking-wide text-text-muted">
                      {t('search.pages')}
                    </p>
                  )}
                  {showSectionHeader && showEntitySection && (
                    <p className="px-4 pb-1 pt-2 text-xs font-medium uppercase tracking-wide text-text-muted">
                      {item.category}
                    </p>
                  )}
                  <button
                    type="button"
                    className={cn(
                      'flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm',
                      index === selectedIndex
                        ? 'bg-accent-muted text-accent'
                        : 'text-text hover:bg-surface-overlay'
                    )}
                    onClick={() => {
                      navigate(item.path);
                      onClose();
                    }}
                    onMouseEnter={() => setSelectedIndex(index)}
                  >
                    {item.kind === 'command' ? (
                      <>
                        <item.icon className="h-4 w-4 shrink-0" />
                        <span>{t(`nav.${item.label}`, { defaultValue: item.label })}</span>
                      </>
                    ) : (
                      <>
                        <item.icon className="h-4 w-4 shrink-0 text-text-muted" />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate">{item.label}</span>
                          {item.sublabel && (
                            <span className="block truncate text-xs text-text-muted">
                              {item.sublabel}
                            </span>
                          )}
                        </span>
                      </>
                    )}
                  </button>
                </li>
              );
            })
          )}
        </ul>
      </div>
      <button
        type="button"
        className="fixed inset-0 -z-10"
        aria-label="Close command palette"
        onClick={onClose}
      />
    </div>
  );
}

export function useCommandPalette() {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        setOpen((prev) => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return { open, setOpen, toggle: () => setOpen((p) => !p), close: () => setOpen(false) };
}
