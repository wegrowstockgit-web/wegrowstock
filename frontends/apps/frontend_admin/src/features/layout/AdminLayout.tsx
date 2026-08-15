import { NavLink, Navigate, Outlet, useNavigate } from 'react-router-dom';
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
  LogOut,
  ScrollText,
  ShieldCheck,
  Webhook,
} from 'lucide-react';
import { cn } from '@invsys/shared-ui';
import { adminLogout } from '@/features/tenants/api';
import { useAdminSession } from '@/features/auth/adminSession';

const navItems = [
  { to: '/', label: 'Tenants', icon: Building2, end: true },
  { to: '/billing', label: 'Platform Billing', icon: CreditCard, end: false },
  { to: '/copilot/knowledge', label: 'Copilot Knowledge', icon: BookOpen, end: false },
  { to: '/integrations', label: 'Webhooks & Integrations', icon: Webhook, end: false },
  { to: '/audit', label: 'Audit Trail', icon: ScrollText, end: false },
  { to: '/shards', label: 'Shard Routing', icon: Database, end: false },
  { to: '/operations/dlq', label: 'Dead Letter Queue', icon: AlertTriangle, end: false },
  { to: '/telemetry', label: 'Concurrency', icon: Gauge, end: false },
  { to: '/compliance', label: 'Global Compliance', icon: ShieldCheck, end: false },
  { to: '/reports/commercial', label: 'Commercial Reports', icon: BarChart3, end: false },
  { to: '/reports/health', label: 'Health Reports', icon: Activity, end: false },
] as const;

export function AdminLayout() {
  const navigate = useNavigate();
  const authenticated = useAdminSession((s) => s.authenticated);
  const email = useAdminSession((s) => s.email);
  const clear = useAdminSession((s) => s.clear);

  const logoutMutation = useMutation({
    mutationFn: adminLogout,
    onSettled: () => {
      clear();
      navigate('/login', { replace: true });
    },
  });

  if (!authenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-surface text-text" data-testid="admin-layout">
      <header className="border-b border-border bg-surface-raised">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
              admin.invsys.com
            </p>
            <h1 className="text-xl font-semibold tracking-tight">Control Plane</h1>
          </div>
          {email ? (
            <p className="text-sm text-text-muted" data-testid="admin-session-email">
              {email}
            </p>
          ) : null}
        </div>
        <nav
          className="mx-auto flex max-w-7xl flex-wrap gap-1 px-6 pb-3"
          aria-label="Control plane navigation"
        >
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-accent/15 text-accent'
                    : 'text-text-muted hover:bg-surface-overlay hover:text-text',
                )
              }
            >
              <Icon className="h-4 w-4" aria-hidden />
              {label}
            </NavLink>
          ))}
          <button
            type="button"
            className="ml-auto inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:bg-surface-overlay hover:text-text"
            onClick={() => logoutMutation.mutate()}
            disabled={logoutMutation.isPending}
            data-testid="admin-logout"
          >
            <LogOut className="h-4 w-4" aria-hidden />
            Logout
          </button>
        </nav>
      </header>
      <main className="mx-auto max-w-7xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
