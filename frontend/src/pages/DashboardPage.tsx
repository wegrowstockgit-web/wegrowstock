import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowDown,
  ArrowRight,
  ArrowUp,
  CheckCircle2,
  Circle,
  ClipboardList,
  DollarSign,
  FileText,
  PackagePlus,
  ScanLine,
  ShoppingCart,
} from 'lucide-react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { apiClient } from '@/api/client';
import type {
  DashboardKpiTrends,
  DashboardRecentOrder,
  DashboardStats,
  DashboardWorkQueue,
  ForecastAlert,
  FulfillmentException,
  LowStockVelocityPoint,
  PaginatedResponse,
  ProductVariant,
} from '@/api/types';
import { WorkQueue } from '@/components/dashboard/WorkQueue';
import { LaborAnalyticsDashboard } from '@/features/dashboard/LaborAnalyticsDashboard';
import { LaborVelocityLeaderboard } from '@/features/dashboard/LaborVelocityLeaderboard';
import { RecentLedgerActivity } from '@/features/inventory/RecentLedgerActivity';
import { useDashboardStream } from '@/hooks/useDashboardStream';
import { SyncConflictAlertBanner } from '@/features/offline/SyncConflictAlertBanner';
import { CardSkeleton, Skeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { formatCurrency, formatNumber, cn } from '@/lib/utils';
import { useSessionStore } from '@/stores/session';

const EMPTY_STATS = {
  stockValue: 0,
  currency: 'USD',
  lowStockCount: 0,
  openOrdersCount: 0,
  unpaidInvoicesCount: 0,
} as const;

function normalizeStats(data?: Partial<DashboardStats> | null): DashboardStats {
  return {
    stockValue: Number(data?.stockValue ?? EMPTY_STATS.stockValue),
    currency: data?.currency ?? EMPTY_STATS.currency,
    lowStockCount: Number(data?.lowStockCount ?? EMPTY_STATS.lowStockCount),
    openOrdersCount: Number(data?.openOrdersCount ?? EMPTY_STATS.openOrdersCount),
    unpaidInvoicesCount: Number(data?.unpaidInvoicesCount ?? EMPTY_STATS.unpaidInvoicesCount),
  };
}

const ORDER_STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  CONFIRMED: 'bg-accent-muted text-accent',
  ALLOCATED: 'bg-accent-muted text-accent',
  PARTIALLY_SHIPPED: 'bg-warning/10 text-warning',
  SHIPPED: 'bg-success/10 text-success',
  CLOSED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
};

function OrderStatusBadge({ status }: { status: string }) {
  const style = ORDER_STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted';
  return (
    <span className={cn('inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium', style)}>
      {status.replaceAll('_', ' ')}
    </span>
  );
}

function TrendIndicator({ trend }: { trend?: 'UP' | 'DOWN' | 'FLAT' }) {
  if (!trend || trend === 'FLAT') {
    return <span className="text-xs text-text-muted">—</span>;
  }
  const Icon = trend === 'UP' ? ArrowUp : ArrowDown;
  const color = trend === 'UP' ? 'text-warning' : 'text-success';
  return (
    <span className={cn('inline-flex items-center gap-0.5 text-xs font-medium', color)}>
      <Icon className="h-3.5 w-3.5" aria-hidden />
      {trend}
    </span>
  );
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 5) return 'Working late';
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

function OnboardingChecklist({
  productCount,
  orderCount,
  canManage,
}: {
  productCount: number;
  orderCount: number;
  canManage: boolean;
}) {
  const navigate = useNavigate();
  const steps = [
    {
      id: 'products',
      label: 'Add your first product',
      done: productCount > 0,
      action: () => navigate('/products'),
      cta: 'Go to products',
      show: canManage,
    },
    {
      id: 'receive',
      label: 'Receive stock via fulfillment scan',
      done: productCount > 0 && orderCount >= 0,
      action: () => navigate('/fulfillment'),
      cta: 'Open scanner',
      show: true,
    },
    {
      id: 'order',
      label: 'Create your first sales order',
      done: orderCount > 0,
      action: () => navigate('/sales-orders'),
      cta: 'Create order',
      show: canManage,
    },
  ].filter((s) => s.show);

  const completed = steps.filter((s) => s.done).length;
  if (completed === steps.length) return null;

  return (
    <section className="mb-6 rounded-2xl bg-surface-raised p-5 shadow-card">
      <h2 className="text-sm font-semibold text-text">Getting started</h2>
      <p className="mt-0.5 text-sm text-text-muted">
        {completed} of {steps.length} steps complete
      </p>
      <ul className="mt-4 space-y-3">
        {steps.map((step) => (
          <li key={step.id} className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              {step.done ? (
                <CheckCircle2 className="h-5 w-5 shrink-0 text-success" />
              ) : (
                <Circle className="h-5 w-5 shrink-0 text-accent" />
              )}
              <span className={step.done ? 'text-text-muted line-through' : 'text-text'}>
                {step.label}
              </span>
            </div>
            {!step.done && (
              <Button variant="secondary" size="sm" onClick={step.action}>
                {step.cta}
              </Button>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function ActivityFeed({
  orders,
  loading,
  canManage,
}: {
  orders?: DashboardRecentOrder[];
  loading: boolean;
  canManage: boolean;
}) {
  const navigate = useNavigate();

  return (
    <section className="rounded-2xl bg-surface-raised p-5 shadow-card" data-testid="activity-feed">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-text">Activity feed</h2>
          <p className="text-sm text-text-muted">Recent order timeline</p>
        </div>
        <Link
          to="/sales-orders"
          className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"
        >
          View all
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </div>

      {loading ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full rounded-xl" />
          ))}
        </div>
      ) : !orders || orders.length === 0 ? (
        <EmptyState
          icon={ShoppingCart}
          title="No orders yet"
          description={
            canManage
              ? 'Create your first sales order to see it here.'
              : 'Sales orders will appear here as they come in.'
          }
          action={
            canManage ? (
              <Button size="sm" onClick={() => navigate('/sales-orders')}>
                Create sales order
              </Button>
            ) : undefined
          }
        />
      ) : (
        <ol className="relative space-y-0 border-l border-border pl-5">
          {orders.map((order, index) => (
            <li key={order.id} className="relative pb-5 last:pb-0">
              <span
                className={cn(
                  'absolute -left-[1.4rem] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-surface-raised',
                  index === 0 ? 'bg-accent' : 'bg-border-strong'
                )}
                aria-hidden
              />
              <button
                type="button"
                onClick={() => navigate('/sales-orders')}
                className="w-full rounded-xl px-2 py-1.5 text-left transition-colors hover:bg-surface-overlay"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-mono text-sm font-semibold text-text">{order.number}</p>
                  <OrderStatusBadge status={order.status} />
                </div>
                <p className="mt-0.5 text-sm text-text-muted">{order.customerName}</p>
                <p className="mt-1 text-xs text-text-muted">
                  {new Date(order.createdAt).toLocaleString(undefined, {
                    month: 'short',
                    day: 'numeric',
                    hour: 'numeric',
                    minute: '2-digit',
                  })}
                </p>
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

export function DashboardPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = useSessionStore((s) => s.user);
  const hasRole = useSessionStore((s) => s.hasRole);
  const isPickerOnly = useSessionStore((s) => s.isPickerOnly());

  const canManageOrders = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const canScan = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: async () => (await apiClient.get<DashboardStats>('/api/v1/dashboard/stats')).data,
    retry: false,
  });

  const { data: recentOrders, isLoading: ordersLoading } = useQuery({
    queryKey: ['dashboard', 'recent-orders'],
    queryFn: async () =>
      (await apiClient.get<DashboardRecentOrder[]>('/api/v1/dashboard/recent-orders')).data,
    retry: false,
  });

  const { data: lowStock, isLoading: lowStockLoading } = useQuery({
    queryKey: ['forecasting', 'alerts'],
    queryFn: async () => (await apiClient.get<ForecastAlert[]>('/api/v1/forecasting/alerts')).data,
    retry: false,
  });

  const { data: kpiTrends } = useQuery({
    queryKey: ['dashboard', 'kpi-trends'],
    queryFn: async () =>
      (await apiClient.get<DashboardKpiTrends>('/api/v1/dashboard/kpi-trends')).data,
    retry: false,
  });

  const { data: velocityChart = [] } = useQuery({
    queryKey: ['dashboard', 'low-stock-velocity'],
    queryFn: async () =>
      (await apiClient.get<LowStockVelocityPoint[]>('/api/v1/dashboard/low-stock-velocity')).data,
    retry: false,
  });

  const { data: workQueue } = useQuery({
    queryKey: ['dashboard', 'work-queue'],
    queryFn: async () =>
      (await apiClient.get<DashboardWorkQueue>('/api/v1/dashboard/work-queue')).data,
    retry: false,
  });

  const { data: openExceptions = [] } = useQuery({
    queryKey: ['office', 'exceptions', 'open'],
    queryFn: async () => {
      const all = (await apiClient.get<FulfillmentException[]>('/api/v1/office/exceptions/list')).data;
      return all.filter((ex) => ex.resolutionStatus === 'OPEN');
    },
    enabled: canManageOrders,
    retry: false,
  });

  // Reactive invalidation via SSE (replaces refetchInterval polling).
  useDashboardStream(true);

  const draftPoMutation = useMutation({
    mutationFn: async (variantIds: string[]) => {
      const res = await apiClient.post<Array<{ id: string; number: string; supplierId: string }>>(
        '/api/v1/forecasting/draft-po',
        { variantIds }
      );
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      navigate('/purchase-orders');
    },
  });

  const { data: productsPage } = useQuery({
    queryKey: ['dashboard', 'product-count'],
    queryFn: async () =>
      (await apiClient.get<PaginatedResponse<ProductVariant>>('/api/v1/variants?limit=1')).data,
    retry: false,
  });

  const stats = normalizeStats(data);
  const productCount = productsPage?.items?.length ?? 0;
  const orderCount = recentOrders?.length ?? 0;
  const primaryRole = user?.roles[0]?.replaceAll('_', ' ') ?? 'User';

  const kpis = [
    {
      title: 'Stock value',
      value: formatCurrency(stats.stockValue, stats.currency),
      hint: 'On-hand inventory at sale price',
      icon: DollarSign,
      iconTone: 'bg-accent-muted text-accent',
      to: '/products',
      trend: kpiTrends?.stockValueTrend,
    },
    {
      title: 'Low stock items',
      value: formatNumber(stats.lowStockCount),
      hint: stats.lowStockCount > 0 ? 'Below reorder point — restock soon' : 'All items above reorder point',
      icon: AlertTriangle,
      iconTone: 'bg-warning/10 text-warning',
      to: '/purchase-orders',
      trend: kpiTrends?.lowStockTrend,
    },
    {
      title: 'Open orders',
      value: formatNumber(stats.openOrdersCount),
      hint: 'Confirmed or allocated, not yet shipped',
      icon: ShoppingCart,
      iconTone: 'bg-success/10 text-success',
      to: '/sales-orders',
      trend: kpiTrends?.openOrdersTrend,
    },
    {
      title: 'Unpaid invoices',
      value: formatNumber(stats.unpaidInvoicesCount),
      hint: 'Open or partially paid',
      icon: FileText,
      iconTone: 'bg-danger/10 text-danger',
      to: '/invoices',
      trend: kpiTrends?.unpaidInvoicesTrend,
    },
  ];

  if (isPickerOnly) {
    return (
      <div className="mx-auto max-w-3xl p-6">
        <div className="rounded-2xl bg-surface-raised p-8 text-center shadow-elevated">
          <ScanLine className="mx-auto mb-4 h-12 w-12 text-accent" />
          <h1 className="text-2xl font-bold text-text">Ready to pick?</h1>
          <p className="mt-2 text-sm text-text-muted">
            Your workspace is optimized for scan-first fulfillment.
          </p>
          <Button className="mt-6" size="lg" onClick={() => navigate('/fulfillment')}>
            <ScanLine className="h-5 w-5" />
            Open fulfillment scanner
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl overflow-x-hidden p-4 sm:p-6">
      <div className="grid grid-cols-12 gap-4 sm:gap-6">
        <header className="col-span-12 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-accent">{primaryRole}</p>
            <h1 className="mt-1 text-2xl font-bold text-text text-balance">
              {greeting()}, {user?.displayName ?? 'there'}
            </h1>
            <p className="mt-1 text-sm text-text-muted">
              {new Date().toLocaleDateString(undefined, {
                weekday: 'long',
                month: 'long',
                day: 'numeric',
              })}
              {isError && ' — live stats unavailable, showing defaults'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {canScan && (
              <Button variant="secondary" onClick={() => navigate('/fulfillment')}>
                <ScanLine className="h-4 w-4" />
                Start scanning
              </Button>
            )}
            {canManageOrders && (
              <>
                <Button variant="secondary" onClick={() => navigate('/purchase-orders')}>
                  <ClipboardList className="h-4 w-4" />
                  New purchase order
                </Button>
                <Button onClick={() => navigate('/sales-orders')}>
                  <PackagePlus className="h-4 w-4" />
                  New sales order
                </Button>
              </>
            )}
          </div>
        </header>

        <div className="col-span-12">
          <OnboardingChecklist
            productCount={productCount}
            orderCount={orderCount}
            canManage={canManageOrders}
          />
        </div>

        {canManageOrders && (
          <div className="col-span-12">
            <SyncConflictAlertBanner />
          </div>
        )}

        {canManageOrders && (
          <div className="col-span-12">
            <WorkQueue queue={workQueue} />
          </div>
        )}

        {canManageOrders && openExceptions.length > 0 && (
          <section
            className="col-span-12 rounded-lg border border-warning/40 bg-warning/5 p-4"
            data-testid="unresolved-exceptions-panel"
          >
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-warning" aria-hidden />
                <div>
                  <h2 className="text-sm font-semibold text-text">Unresolved Exceptions</h2>
                  <p className="text-xs text-text-muted">
                    Floor Skip &amp; Flag reports awaiting office resolution
                  </p>
                </div>
              </div>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => navigate('/exceptions?tab=holds')}
                data-testid="open-exceptions-queue"
              >
                Open queue ({openExceptions.length})
              </Button>
            </div>
            <ul className="mt-3 space-y-2" data-testid="unresolved-exceptions-list">
              {openExceptions.slice(0, 3).map((ex) => (
                <li
                  key={ex.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-surface px-3 py-2 text-sm"
                >
                  <span className="font-mono text-xs text-text-muted">
                    {ex.allocationId.slice(0, 8)}…
                  </span>
                  <span className="font-medium text-warning">
                    {String(ex.metadata?.reason ?? 'OPEN')}
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => navigate('/exceptions?tab=holds')}
                  >
                    Resolve
                  </Button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {isLoading ? (
          <div className="col-span-12 grid grid-cols-12 gap-4" data-testid="floating-kpi-row">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="col-span-12 sm:col-span-6 xl:col-span-3">
                <CardSkeleton />
              </div>
            ))}
          </div>
        ) : (
          <div className="col-span-12 grid grid-cols-12 gap-4" data-testid="floating-kpi-row">
            {kpis.map((kpi) => {
              const Icon = kpi.icon;
              return (
                <Link
                  key={kpi.title}
                  to={kpi.to}
                  data-testid={`kpi-${kpi.title.toLowerCase().replace(/\s+/g, '-')}`}
                  className={cn(
                    'col-span-12 sm:col-span-6 xl:col-span-3',
                    'group rounded-2xl bg-surface-raised p-5 shadow-card',
                    'transition-[box-shadow,transform] duration-200 ease-out',
                    'hover:shadow-elevated motion-safe:hover:-translate-y-0.5',
                    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
                  )}
                >
                  <div className="flex items-start justify-between">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-text-muted">{kpi.title}</p>
                      <div className="mt-2 flex min-w-0 items-baseline gap-2">
                        <p className="min-w-0 text-[clamp(1.25rem,3.2vw,1.875rem)] font-bold leading-tight tabular-nums text-text">
                          {kpi.value}
                        </p>
                        <TrendIndicator trend={kpi.trend} />
                      </div>
                      <p className="mt-1.5 text-xs text-text-muted">{kpi.hint}</p>
                    </div>
                    <div className={cn('shrink-0 rounded-xl p-2.5', kpi.iconTone)}>
                      <Icon className="h-5 w-5" aria-hidden />
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        )}

        {canManageOrders && (
          <div className="col-span-12 grid grid-cols-12 gap-4 sm:gap-6">
            <div className="col-span-12 lg:col-span-6">
              <LaborAnalyticsDashboard mode="summary" />
            </div>
            <div className="col-span-12 lg:col-span-6 space-y-4">
              <LaborVelocityLeaderboard mode="summary" />
              <RecentLedgerActivity />
            </div>
          </div>
        )}

        <div className="col-span-12 grid grid-cols-12 gap-4 sm:gap-6">
          <div className="col-span-12 space-y-6 xl:col-span-7">
            <ActivityFeed
              orders={recentOrders}
              loading={ordersLoading}
              canManage={canManageOrders}
            />

            <section className="rounded-2xl bg-surface-raised p-5 shadow-card">
              <h2 className="text-sm font-semibold text-text">Low stock velocity</h2>
              <p className="text-sm text-text-muted">
                Projected depletion trend for items below reorder point
              </p>
              {velocityChart.length === 0 ? (
                <p className="mt-4 text-sm text-text-muted">No low-stock depletion data yet.</p>
              ) : (
                <div className="mt-4 h-48 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={velocityChart}>
                      <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                      <XAxis
                        dataKey="date"
                        tickFormatter={(d) =>
                          new Date(d).toLocaleDateString(undefined, {
                            month: 'short',
                            day: 'numeric',
                          })
                        }
                        className="text-xs"
                      />
                      <YAxis className="text-xs" />
                      <Tooltip
                        formatter={(value) => [
                          formatNumber(Number(value ?? 0)),
                          'Available units',
                        ]}
                        labelFormatter={(label) => new Date(label).toLocaleDateString()}
                      />
                      <Line
                        type="monotone"
                        dataKey="availableUnits"
                        stroke="var(--color-accent)"
                        strokeWidth={2}
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
            </section>
          </div>

          <section className="col-span-12 rounded-2xl bg-surface-raised p-5 shadow-card xl:col-span-5">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 className="text-sm font-semibold text-text">Low stock</h2>
                <p className="text-sm text-text-muted">Available below reorder point</p>
              </div>
              {canManageOrders && lowStock && lowStock.length > 0 && (
                <Button
                  size="sm"
                  variant="secondary"
                  loading={draftPoMutation.isPending}
                  onClick={() =>
                    draftPoMutation.mutate(lowStock.slice(0, 5).map((item) => item.variantId))
                  }
                >
                  Generate draft PO
                </Button>
              )}
            </div>

            {lowStockLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : !lowStock || lowStock.length === 0 ? (
              <EmptyState
                icon={AlertTriangle}
                title="Nothing to restock"
                description="Every item is above its reorder point."
              />
            ) : (
              <ul className="divide-y divide-border">
                {lowStock.map((item) => (
                  <li
                    key={item.variantId}
                    className="flex items-center justify-between gap-3 py-3"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-text">{item.productName}</p>
                      <p className="mt-0.5 font-mono text-xs text-text-muted">{item.sku}</p>
                    </div>
                    <div className="flex shrink-0 items-center gap-3">
                      {canManageOrders && (
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={draftPoMutation.isPending}
                          onClick={() => draftPoMutation.mutate([item.variantId])}
                        >
                          Quick PO
                        </Button>
                      )}
                      <div className="text-right">
                        <p className="text-sm font-semibold tabular-nums text-warning">
                          {formatNumber(item.available)}
                        </p>
                        <p className="text-xs tabular-nums text-text-muted">
                          rec. PO {formatNumber(item.recommendedPoQty)} · vel{' '}
                          {formatNumber(item.velocity30d)}/d
                        </p>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
