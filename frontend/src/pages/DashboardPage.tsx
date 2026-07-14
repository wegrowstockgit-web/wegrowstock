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
  LowStockVelocityPoint,
  PaginatedResponse,
  ProductVariant,
} from '@/api/types';
import { WorkQueue } from '@/components/dashboard/WorkQueue';

import { Card, CardHeader } from '@/components/ui/Card';

import { CardSkeleton, Skeleton } from '@/components/ui/Skeleton';

import { Button } from '@/components/ui/Button';

import {

  Table,

  TableBody,

  TableCell,

  TableHead,

  TableHeader,

  TableRow,

} from '@/components/ui/Table';

import { EmptyState } from '@/components/ui/EmptyState';

import { formatCurrency, formatNumber } from '@/lib/utils';

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

  CONFIRMED: 'bg-[#55ACEE]/15 text-[#1d6fbf]',

  ALLOCATED: 'bg-[#55ACEE]/15 text-[#1d6fbf]',

  PARTIALLY_SHIPPED: 'bg-warning/10 text-warning',

  SHIPPED: 'bg-success/10 text-success',

  CLOSED: 'bg-success/10 text-success',

  CANCELLED: 'bg-danger/10 text-danger',

};



function OrderStatusBadge({ status }: { status: string }) {

  const style = ORDER_STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted';

  return (

    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${style}`}>

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
    <span className={`inline-flex items-center gap-0.5 text-xs font-medium ${color}`}>
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

    <Card className="mb-6 border-[#55ACEE]/25 bg-gradient-to-r from-[#55ACEE]/8 to-transparent" padding="md">

      <CardHeader

        title="Getting started"

        description={`${completed} of ${steps.length} steps complete — get live in minutes`}

      />

      <ul className="space-y-3">

        {steps.map((step) => (

          <li key={step.id} className="flex items-center justify-between gap-4">

            <div className="flex items-center gap-3">

              {step.done ? (

                <CheckCircle2 className="h-5 w-5 shrink-0 text-success" />

              ) : (

                <Circle className="h-5 w-5 shrink-0 text-[#55ACEE]" />

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

    </Card>

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

    queryFn: async () => {

      const res = await apiClient.get<DashboardStats>('/api/v1/dashboard/stats');

      return res.data;

    },

    retry: false,

  });



  const { data: recentOrders, isLoading: ordersLoading } = useQuery({

    queryKey: ['dashboard', 'recent-orders'],

    queryFn: async () => {

      const res = await apiClient.get<DashboardRecentOrder[]>(

        '/api/v1/dashboard/recent-orders'

      );

      return res.data;

    },

    retry: false,

  });



  const { data: lowStock, isLoading: lowStockLoading } = useQuery({

    queryKey: ['forecasting', 'alerts'],

    queryFn: async () => {

      const res = await apiClient.get<ForecastAlert[]>(

        '/api/v1/forecasting/alerts'

      );

      return res.data;

    },

    retry: false,

  });

  const { data: kpiTrends } = useQuery({
    queryKey: ['dashboard', 'kpi-trends'],
    queryFn: async () => {
      const res = await apiClient.get<DashboardKpiTrends>('/api/v1/dashboard/kpi-trends');
      return res.data;
    },
    retry: false,
  });

  const { data: velocityChart = [] } = useQuery({
    queryKey: ['dashboard', 'low-stock-velocity'],
    queryFn: async () => {
      const res = await apiClient.get<LowStockVelocityPoint[]>('/api/v1/dashboard/low-stock-velocity');
      return res.data;
    },
    retry: false,
  });

  const { data: workQueue } = useQuery({
    queryKey: ['dashboard', 'work-queue'],
    queryFn: async () => {
      const res = await apiClient.get<DashboardWorkQueue>('/api/v1/dashboard/work-queue');
      return res.data;
    },
    retry: false,
  });

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

    queryFn: async () => {

      const res = await apiClient.get<PaginatedResponse<ProductVariant>>(

        '/api/v1/variants?limit=1'

      );

      return res.data;

    },

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

      accent: 'border-l-[#55ACEE]',

      iconBg: 'bg-[#55ACEE]/15 text-[#1d6fbf]',

      to: '/products',

      trend: kpiTrends?.stockValueTrend,

    },

    {

      title: 'Low stock items',

      value: formatNumber(stats.lowStockCount),

      hint: stats.lowStockCount > 0 ? 'Below reorder point — restock soon' : 'All items above reorder point',

      icon: AlertTriangle,

      accent: 'border-l-warning',

      iconBg: 'bg-warning/10 text-warning',

      to: '/purchase-orders',

      trend: kpiTrends?.lowStockTrend,

    },

    {

      title: 'Open orders',

      value: formatNumber(stats.openOrdersCount),

      hint: 'Confirmed or allocated, not yet shipped',

      icon: ShoppingCart,

      accent: 'border-l-success',

      iconBg: 'bg-success/10 text-success',

      to: '/sales-orders',

      trend: kpiTrends?.openOrdersTrend,

    },

    {

      title: 'Unpaid invoices',

      value: formatNumber(stats.unpaidInvoicesCount),

      hint: 'Open or partially paid',

      icon: FileText,

      accent: 'border-l-danger',

      iconBg: 'bg-danger/10 text-danger',

      to: '/invoices',

      trend: kpiTrends?.unpaidInvoicesTrend,

    },

  ];



  if (isPickerOnly) {

    return (

      <div className="mx-auto max-w-3xl p-6">

        <div className="rounded-xl border border-[#55ACEE]/30 bg-gradient-to-br from-[#55ACEE]/10 to-transparent p-8 text-center">

          <ScanLine className="mx-auto mb-4 h-12 w-12 text-[#55ACEE]" />

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

    <div className="mx-auto max-w-7xl p-6">

      <div className="mb-6 overflow-hidden rounded-xl border border-border bg-surface-raised shadow-card">

        <div className="h-1.5 bg-gradient-to-r from-[#55ACEE] via-[#1d6fbf] to-[#55ACEE]/40" />

        <div className="flex flex-wrap items-end justify-between gap-4 p-6">

          <div>

            <p className="text-xs font-semibold uppercase tracking-wider text-[#55ACEE]">

              {primaryRole}

            </p>

            <h1 className="mt-1 text-2xl font-bold text-text">

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

              <Button

                variant="secondary"

                onClick={() => navigate('/fulfillment')}

                className="border-[#55ACEE]/30"

              >

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

                <Button

                  onClick={() => navigate('/sales-orders')}

                  className="bg-[#55ACEE] hover:bg-[#4a9de0]"

                >

                  <PackagePlus className="h-4 w-4" />

                  New sales order

                </Button>

              </>

            )}

          </div>

        </div>

      </div>



      <OnboardingChecklist

        productCount={productCount}

        orderCount={orderCount}

        canManage={canManageOrders}

      />

      {canManageOrders && <WorkQueue queue={workQueue} />}

      {isLoading ? (

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">

          {Array.from({ length: 4 }).map((_, i) => (

            <CardSkeleton key={i} />

          ))}

        </div>

      ) : (

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">

          {kpis.map((kpi) => {

            const Icon = kpi.icon;

            return (

              <Link

                key={kpi.title}

                to={kpi.to}

                className={`group rounded-lg border border-border border-l-4 bg-surface-raised p-5 shadow-card transition-all hover:border-[#55ACEE]/40 hover:shadow-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#55ACEE] ${kpi.accent}`}

              >

                <div className="flex items-start justify-between">

                  <div className="min-w-0">

                    <p className="text-sm font-medium text-text-muted">{kpi.title}</p>

                    <div className="mt-2 flex items-center gap-2">
                      <p className="truncate text-3xl font-bold tabular-nums text-text">
                        {kpi.value}
                      </p>
                      <TrendIndicator trend={kpi.trend} />
                    </div>

                    <p className="mt-1.5 text-xs text-text-muted">{kpi.hint}</p>

                  </div>

                  <div className={`shrink-0 rounded-lg p-2.5 ${kpi.iconBg}`}>

                    <Icon className="h-5 w-5" aria-hidden="true" />

                  </div>

                </div>

              </Link>

            );

          })}

        </div>

      )}



      <Card className="mt-6" padding="md">
        <CardHeader
          title="Low stock velocity"
          description="Projected depletion trend for items below reorder point"
        />
        {velocityChart.length === 0 ? (
          <p className="text-sm text-text-muted">No low-stock depletion data yet.</p>
        ) : (
          <div className="h-48 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={velocityChart}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis
                  dataKey="date"
                  tickFormatter={(d) =>
                    new Date(d).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
                  }
                  className="text-xs"
                />
                <YAxis className="text-xs" />
                  <Tooltip
                    formatter={(value) => [formatNumber(Number(value ?? 0)), 'Available units']}
                  labelFormatter={(label) => new Date(label).toLocaleDateString()}
                />
                <Line
                  type="monotone"
                  dataKey="availableUnits"
                  stroke="#55ACEE"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">

        <Card className="xl:col-span-2" padding="md">

          <CardHeader

            title="Recent sales orders"

            description="Latest activity across all channels"

            action={

              <Link

                to="/sales-orders"

                className="inline-flex items-center gap-1 text-sm font-medium text-[#55ACEE] hover:underline"

              >

                View all

                <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />

              </Link>

            }

          />

          {ordersLoading ? (

            <div className="space-y-3">

              {Array.from({ length: 4 }).map((_, i) => (

                <Skeleton key={i} className="h-10 w-full" />

              ))}

            </div>

          ) : !recentOrders || recentOrders.length === 0 ? (

            <EmptyState

              icon={ShoppingCart}

              title="No orders yet"

              description={

                canManageOrders

                  ? 'Create your first sales order to see it here.'

                  : 'Sales orders will appear here as they come in.'

              }

              action={

                canManageOrders ? (

                  <Button size="sm" onClick={() => navigate('/sales-orders')}>

                    Create sales order

                  </Button>

                ) : undefined

              }

            />

          ) : (

            <Table>

              <TableHeader>

                <TableRow>

                  <TableHead>Order</TableHead>

                  <TableHead>Customer</TableHead>

                  <TableHead>Status</TableHead>

                  <TableHead align="right">Created</TableHead>

                </TableRow>

              </TableHeader>

              <TableBody>

                {recentOrders.map((order) => (

                  <TableRow key={order.id} onClick={() => navigate('/sales-orders')}>

                    <TableCell mono>{order.number}</TableCell>

                    <TableCell>{order.customerName}</TableCell>

                    <TableCell>

                      <OrderStatusBadge status={order.status} />

                    </TableCell>

                    <TableCell align="right" className="text-text-muted">

                      {new Date(order.createdAt).toLocaleDateString(undefined, {

                        month: 'short',

                        day: 'numeric',

                      })}

                    </TableCell>

                  </TableRow>

                ))}

              </TableBody>

            </Table>

          )}

        </Card>

        <Card padding="md">

          <CardHeader

            title="Low stock"

            description="Available below reorder point"

            action={

              canManageOrders ? (

                <div className="flex items-center gap-2">
                  {lowStock && lowStock.length > 0 && (
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
                  <Link

                  to="/purchase-orders"

                  className="inline-flex items-center gap-1 text-sm font-medium text-[#55ACEE] hover:underline"

                >

                  Restock

                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />

                </Link>
                </div>

              ) : undefined

            }

          />

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

                <li key={item.variantId} className="flex items-center justify-between gap-3 py-3">

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

                      rec. PO {formatNumber(item.recommendedPoQty)} · vel {formatNumber(item.velocity30d)}/d

                    </p>

                  </div>
                  </div>

                </li>

              ))}

            </ul>

          )}

        </Card>

      </div>

    </div>

  );

}


