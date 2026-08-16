import { useMemo, useState } from 'react';
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  BookOpen,
  Building2,
  CreditCard,
  Database,
  Gauge,
  Layers,
  LogOut,
  Menu,
  ScrollText,
  Search,
  ShieldCheck,
  Webhook,
  X,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '@invsys/shared-ui';
import { adminLogout } from '@/features/tenants/api';
import { useAdminSession } from '@/features/auth/adminSession';

type NavItem = {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
};

type NavGroup = {
  label: string;
  items: NavItem[];
};

const navGroups: NavGroup[] = [
  {
    label: 'Commercial',
    items: [
      { to: '/', label: 'Tenants', icon: Building2, end: true },
      { to: '/billing', label: 'Platform Billing', icon: CreditCard },
      { to: '/packaging', label: 'Pricing & Packaging', icon: Layers },
    ],
  },
  {
    label: 'Platform',
    items: [
      { to: '/copilot/knowledge', label: 'Copilot Knowledge', icon: BookOpen },
      { to: '/integrations', label: 'Webhooks & Integrations', icon: Webhook },
      { to: '/audit', label: 'Audit Trail', icon: ScrollText },
      { to: '/compliance', label: 'Global Compliance', icon: ShieldCheck },
    ],
  },
  {
    label: 'Operations',
    items: [
      { to: '/operations/dlq', label: 'Dead Letter Queue', icon: AlertTriangle },
      { to: '/telemetry', label: 'Concurrency', icon: Gauge },
      { to: '/shards', label: 'Shard Routing', icon: Database },
    ],
  },
  {
    label: 'Reports',
    items: [
      { to: '/reports/commercial', label: 'Commercial Reports', icon: BarChart3 },
      { to: '/reports/health', label: 'Health Reports', icon: Activity },
    ],
  },
];

const allNavItems = navGroups.flatMap((group) => group.items);

function currentPageLabel(pathname: string): string {
  const exact = allNavItems.find((item) => item.end && pathname === item.to);
  if (exact) return exact.label;
  const match = allNavItems
    .filter((item) => !item.end && pathname.startsWith(item.to))
    .sort((a, b) => b.to.length - a.to.length)[0];
  return match?.label ?? 'Control Plane';
}

export function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const authenticated = useAdminSession((s) => s.authenticated);
  const email = useAdminSession((s) => s.email);
  const clear = useAdminSession((s) => s.clear);
  const [navOpen, setNavOpen] = useState(false);
  const [query, setQuery] = useState('');

  const logoutMutation = useMutation({
    mutationFn: adminLogout,
    onSettled: () => {
      clear();
      navigate('/login', { replace: true });
    },
  });

  const filteredGroups = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return navGroups;
    return navGroups
      .map((group) => ({
        ...group,
        items: group.items.filter((item) => item.label.toLowerCase().includes(q)),
      }))
      .filter((group) => group.items.length > 0);
  }, [query]);

  if (!authenticated) {
    return <Navigate to="/login" replace />;
  }

  const pageLabel = currentPageLabel(location.pathname);

  const nav = (
    <>
      <div className="px-4 pb-3 pt-5">
        <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-text-muted">
          admin.invsys.com
        </p>
        <p className="mt-1 text-base font-semibold tracking-tight text-text">Control Plane</p>
      </div>

      <label className="relative mx-4 mb-4 block">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-text-muted" />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Find a page"
          className="admin-field h-9 pl-8"
          aria-label="Filter navigation"
        />
      </label>

      <nav className="min-h-0 flex-1 overflow-y-auto px-3 pb-4" aria-label="Control plane navigation">
        {filteredGroups.length === 0 ? (
          <p className="px-2 text-sm text-text-muted">No matching pages.</p>
        ) : (
          filteredGroups.map((group) => (
            <div key={group.label} className="mb-5">
              <p className="px-2 pb-1.5 text-[11px] font-medium uppercase tracking-[0.12em] text-text-muted">
                {group.label}
              </p>
              <div className="space-y-0.5">
                {group.items.map(({ to, label, icon: Icon, end }) => (
                  <NavLink
                    key={to}
                    to={to}
                    end={end}
                    onClick={() => setNavOpen(false)}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm font-medium',
                        'transition-[background-color,color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)]',
                        isActive
                          ? 'bg-accent/15 text-accent'
                          : 'text-text-muted hover:bg-surface-overlay hover:text-text',
                      )
                    }
                  >
                    <Icon className="h-4 w-4 shrink-0" aria-hidden />
                    <span className="truncate">{label}</span>
                  </NavLink>
                ))}
              </div>
            </div>
          ))
        )}
      </nav>

      <div className="border-t border-border p-3">
        {email ? (
          <p className="truncate px-2 text-xs text-text-muted" data-testid="admin-session-email">
            {email}
          </p>
        ) : null}
        <button
          type="button"
          className="mt-2 inline-flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm font-medium text-text-muted transition-[background-color,color] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)] hover:bg-surface-overlay hover:text-text"
          onClick={() => logoutMutation.mutate()}
          disabled={logoutMutation.isPending}
          data-testid="admin-logout"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          {logoutMutation.isPending ? 'Signing out…' : 'Logout'}
        </button>
      </div>
    </>
  );

  return (
    <div className="min-h-screen bg-surface text-text lg:flex" data-testid="admin-layout">
      {navOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-30 bg-text/40 lg:hidden"
          aria-label="Close navigation"
          onClick={() => setNavOpen(false)}
        />
      ) : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-border bg-surface-raised',
          'transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)]',
          'motion-reduce:transition-none',
          navOpen ? 'translate-x-0' : '-translate-x-full',
          'lg:static lg:z-0 lg:translate-x-0',
        )}
      >
        <div className="flex items-center justify-end px-3 pt-3 lg:hidden">
          <button
            type="button"
            className="rounded-md p-2 text-text-muted hover:bg-surface-overlay hover:text-text"
            onClick={() => setNavOpen(false)}
            aria-label="Close navigation"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {nav}
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 border-b border-border bg-surface/95 backdrop-blur-sm">
          <div className="flex items-center gap-3 px-4 py-3 sm:px-6">
            <button
              type="button"
              className="rounded-md p-2 text-text-muted hover:bg-surface-raised hover:text-text lg:hidden"
              onClick={() => setNavOpen(true)}
              aria-label="Open navigation"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-text">{pageLabel}</p>
              <p className="hidden text-xs text-text-muted sm:block">Super Admin workspace</p>
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
