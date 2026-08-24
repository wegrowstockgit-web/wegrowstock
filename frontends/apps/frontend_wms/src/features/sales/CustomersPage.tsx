import { useState, type MouseEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MoreHorizontal, Plus, Users } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Customer, CustomerKpiSummary } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { ListPageState } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { Pagination } from '@/components/ui/Pagination';
import { TableDensityScope } from '@/hooks/useDensity';
import { useClientSort } from '@/hooks/useClientSort';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { listCustomers } from '@/api/operational';
import { CustomerDetail } from '@/features/customers/CustomerDetail';
import { NewCustomerDrawer } from '@/features/sales/NewCustomerDrawer';
import { WholesaleApplicationsPanel } from '@/features/sales/WholesaleApplicationsPanel';
import { cn, formatCurrency } from '@/lib/utils';

function portalLabel(status?: string) {
  if (status === 'ACTIVE') return 'Active';
  if (status === 'PENDING') return 'Pending';
  return 'Not Invited';
}

function CustomersTable({
  items,
  onPeek,
}: {
  items: Customer[];
  onPeek: (customer: Customer) => void;
}) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canInvite = hasRole('OWNER', 'ADMIN');
  const canHold = hasRole('FINANCE_ADMIN', 'OWNER', 'ADMIN');
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      name: (c) => c.name,
      tier: (c) => c.priceTierName ?? '',
      credit: (c) => Number(c.availableCredit ?? c.creditLimit ?? 0),
      terms: (c) => c.paymentTerms ?? '',
      portal: (c) => c.portalStatus ?? '',
    },
    { key: 'name', dir: 'asc' },
  );

  const invite = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/customers/${id}/portal-invite`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['customers'] }),
  });
  const hold = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/customers/${id}/credit-hold`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      void queryClient.invalidateQueries({ queryKey: ['customers-summary'] });
    },
  });

  return (
    <div className="min-w-0 w-full overflow-x-auto scrollbar-thin">
      <Table className="min-w-full table-auto">
        <TableHeader>
          <TableRow>
            <TableHead sortable sortKey="name" sort={sort} onSort={toggle}>
              Name & Email
            </TableHead>
            <TableHead sortable sortKey="tier" sort={sort} onSort={toggle}>
              Price Tier
            </TableHead>
            <TableHead sortable sortKey="credit" sort={sort} onSort={toggle}>
              Credit Available
            </TableHead>
            <TableHead sortable sortKey="terms" sort={sort} onSort={toggle}>
              Terms
            </TableHead>
            <TableHead sortable sortKey="portal" sort={sort} onSort={toggle}>
              Portal Status
            </TableHead>
            <TableHead align="right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((c) => (
            <TableRow
              key={c.id}
              className="cursor-pointer"
              onClick={() => onPeek(c)}
              data-testid={`customer-row-${c.id}`}
            >
              <TableCell>
                <div className="font-medium text-text">{c.name}</div>
                <div className="text-xs text-text-muted">{c.email ?? '—'}</div>
              </TableCell>
              <TableCell data-testid={`customer-tier-${c.id}`}>{c.priceTierName || 'List'}</TableCell>
              <TableCell className="font-mono text-sm" data-testid={`customer-credit-${c.id}`}>
                {formatCurrency(Number(c.availableCredit ?? 0))} /{' '}
                {formatCurrency(Number(c.creditLimit ?? 0))} Limit
              </TableCell>
              <TableCell>{c.paymentTerms ?? '—'}</TableCell>
              <TableCell>
                <span
                  className={cn(
                    'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                    c.portalStatus === 'ACTIVE'
                      ? 'bg-success/10 text-success'
                      : c.portalStatus === 'PENDING'
                        ? 'bg-warning/10 text-warning'
                        : 'bg-surface-overlay text-text-muted',
                  )}
                  data-testid={`customer-portal-${c.id}`}
                >
                  {portalLabel(c.portalStatus)}
                </span>
              </TableCell>
              <TableCell align="right">
                <div onClick={(e: MouseEvent) => e.stopPropagation()}>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        variant="ghost"
                        size="sm"
                        data-testid={`customer-row-actions-${c.id}`}
                        aria-label={`Actions for ${c.name}`}
                      >
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem
                        data-testid={`customer-open-workspace-${c.id}`}
                        onClick={() => navigate(`/sales/customers/${c.id}`)}
                      >
                        View Details Workspace
                      </DropdownMenuItem>
                      {canInvite && (
                        <DropdownMenuItem
                          data-testid={`customer-portal-invite-${c.id}`}
                          disabled={!c.email || c.portalStatus === 'ACTIVE'}
                          onClick={() => invite.mutate(c.id)}
                        >
                          Send B2B Portal Invite
                        </DropdownMenuItem>
                      )}
                      {canHold && c.customerStatus !== 'HOLD' && (
                        <DropdownMenuItem
                          data-testid={`customer-credit-hold-${c.id}`}
                          onClick={() => hold.mutate(c.id)}
                        >
                          Place on Credit Hold
                        </DropdownMenuItem>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

export function CustomersPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [peekCustomer, setPeekCustomer] = useState<Customer | null>(null);
  const [tab, setTab] = useState<'customers' | 'applications'>(() =>
    location.pathname.startsWith('/sales/customers') && !location.pathname.includes('/sales/customers/')
      ? 'applications'
      : 'customers',
  );

  const table = useServerTableQuery<Customer>({
    queryKey: 'customers',
    path: '/api/v1/customers',
    defaultSort: 'name,asc',
    fetcher: listCustomers,
  });
  const { items, isLoading, isError, error, refetch, search } = table;

  const { data: kpis } = useQuery({
    queryKey: ['customers-summary'],
    queryFn: async () => (await apiClient.get<CustomerKpiSummary>('/api/v1/customers/summary')).data,
  });

  return (
    <TableDensityScope gridId="customers">
    <div
      className="mx-auto flex w-full max-w-7xl flex-col p-4 sm:p-6"
      data-testid="customers-page"
    >
      <div className="mb-6 flex shrink-0 flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-text">{t('sales.customersTitle')}</h1>
        {canCreate && (
          <Button onClick={() => setDrawerOpen(true)}>
            <Plus className="h-4 w-4" />
            {t('sales.addCustomer')}
          </Button>
        )}
      </div>

      <div className="mb-4 flex shrink-0 gap-2" role="tablist" aria-label={t('sales.customerViews')}>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'customers'}
          className={cn(
            'rounded-md px-3 py-1.5 text-sm font-medium',
            tab === 'customers' ? 'bg-accent-muted text-accent' : 'text-text-muted hover:bg-surface-overlay',
          )}
          onClick={() => setTab('customers')}
        >
          {t('sales.customersTab')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'applications'}
          data-testid="pending-applications-tab"
          className={cn(
            'rounded-md px-3 py-1.5 text-sm font-medium',
            tab === 'applications' ? 'bg-accent-muted text-accent' : 'text-text-muted hover:bg-surface-overlay',
          )}
          onClick={() => setTab('applications')}
        >
          {t('sales.pendingApplications')}
        </button>
      </div>

      {tab === 'customers' && (
        <>
          <div className="mb-4 grid shrink-0 gap-3 sm:grid-cols-3" data-testid="customer-kpi-row">
            <Card padding="sm" data-testid="kpi-active-b2b">
              <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
                Active B2B Accounts
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums text-text">
                {kpis?.activeB2bAccounts ?? '—'}
              </p>
            </Card>
            <Card padding="sm" data-testid="kpi-credit-hold">
              <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
                Accounts on Credit Hold
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums text-text">
                {kpis?.accountsOnCreditHold ?? '—'}
              </p>
            </Card>
            <Card padding="sm" data-testid="kpi-credit-extended">
              <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
                Total Credit Extended
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums text-text">
                {formatCurrency(Number(kpis?.totalCreditExtended ?? 0))}
              </p>
            </Card>
          </div>

          <div className="shrink-0">
            <DataListToolbar gridId="customers">
              <DebouncedSearchInput
                value={search}
                onDebouncedChange={table.setSearch}
                placeholder="Search customers…"
              />
            </DataListToolbar>
          </div>

          <div className="min-h-0 min-w-0 flex-1">
            <ListPageState
              isLoading={isLoading && items.length === 0}
              isError={isError}
              error={error}
              data={items}
              refetch={refetch}
              emptyIcon={Users}
              emptyTitle={search ? 'No matching customers' : 'No customers yet'}
              emptyDescription={search ? ' ' : ' '}
              emptyAction={
                canCreate ? (
                  <Button onClick={() => setDrawerOpen(true)}>
                    <Plus className="h-4 w-4" />
                    Add customer
                  </Button>
                ) : undefined
              }
            >
              {(rows) => (
                <>
                  <CustomersTable items={rows} onPeek={setPeekCustomer} />
                  <Pagination
                    page={table.page}
                    totalPages={table.totalPages}
                    totalElements={table.totalElements}
                    size={table.size}
                    onPageChange={table.setPage}
                    onSizeChange={table.setSize}
                  />
                </>
              )}
            </ListPageState>
          </div>
        </>
      )}

      {tab === 'applications' && (
        <div className="min-h-0 min-w-0 flex-1">
          <WholesaleApplicationsPanel />
        </div>
      )}

      <NewCustomerDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />

      <RightPeekDrawer
        open={!!peekCustomer}
        onClose={() => setPeekCustomer(null)}
        title={peekCustomer?.name ?? 'Customer'}
        width="lg"
      >
        {peekCustomer ? <CustomerDetail customer={peekCustomer} /> : null}
      </RightPeekDrawer>
    </div>
    </TableDensityScope>
  );
}
