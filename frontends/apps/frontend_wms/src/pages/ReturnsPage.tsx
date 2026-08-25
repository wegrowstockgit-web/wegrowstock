import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardCheck, Download, Plus, RotateCcw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import { unwrapPageItems } from '@/api/page';
import type { PaginatedResponse, Return, ReturnLine, TenantLocation } from '@/api/types';
import { listReturns } from '@/api/operational';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { DensityToggle } from '@/components/ui/DensityToggle';
import { Pagination } from '@/components/ui/Pagination';
import { TableDensityScope } from '@/hooks/useDensity';
import { useCapMobilePageSize } from '@/hooks/useCapMobilePageSize';
import { useClientSort } from '@/hooks/useClientSort';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { useToast } from '@/components/ui/Toast';
import { cn, formatCurrency, formatMediumDate } from '@/lib/utils';
import { RmaInspectionDrawer } from '@/features/returns/RmaInspectionDrawer';
import { NewRmaWizard } from '@/features/returns/NewRmaWizard';
import { restockLinesMissingBin, rmaActionErrorMessage } from '@/features/returns/rmaActionError';

const STATUSES = [
  'ALL',
  'PENDING_REVIEW',
  'REQUESTED',
  'APPROVED',
  'RECEIVED',
  'CLOSED',
  'REJECTED',
] as const;
const DISPOSITIONS = ['RESTOCK', 'SCRAP', 'REPAIR'] as const;
const DISPOSITION_STATUSES = new Set(['REQUESTED', 'APPROVED', 'EXPECTED', 'RECEIVED']);

const STATUS_STYLES: Record<string, string> = {
  REQUESTED: 'bg-warning/20 text-warning',
  PENDING_REVIEW: 'bg-warning/30 text-warning',
  APPROVED: 'bg-accent-muted text-accent',
  RECEIVED: 'bg-success/20 text-success',
  CLOSED: 'bg-surface-overlay text-text-muted',
  REJECTED: 'bg-danger/20 text-danger',
};

function RmaReviewQueue({ canManage }: { canManage: boolean }) {
  const queryClient = useQueryClient();
  const { data: pending = [], isLoading } = useQuery({
    queryKey: ['returns', 'review-queue'],
    queryFn: async () =>
      unwrapPageItems(
        (await apiClient.get<PaginatedResponse<Return> | Return[]>('/api/v1/returns?status=PENDING_REVIEW'))
          .data,
      ),
    retry: false,
  });

  const reviewMutation = useMutation({
    mutationFn: async ({
      id,
      action,
    }: {
      id: string;
      action: 'approve-with-label' | 'approve-without-label' | 'deny';
    }) => {
      await apiClient.post(`/api/v1/returns/${id}/review/${action}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
  });

  if (isLoading || pending.length === 0) {
    return null;
  }

  return (
    <Card className="mb-6" data-testid="rma-review-queue">
      <CardHeader
        title="RMA Review Queue"
        description={`${pending.length} portal return${pending.length === 1 ? '' : 's'} awaiting decision`}
      />
      <div className="space-y-4">
        {pending.map((rma) => (
          <div
            key={rma.id}
            className="rounded-md border border-border bg-surface-raised p-4"
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-mono font-semibold text-text">{rma.number}</p>
                <p className="text-sm text-text-muted">
                  {rma.customerName ?? 'Customer'} · {rma.salesOrderNumber ?? rma.salesOrderId}
                </p>
                <p className="mt-1 text-sm text-text">
                  Reason: <span className="font-medium">{rma.reasonCode ?? '—'}</span>
                </p>
                <p className="mt-1 text-sm text-text-muted">
                  Est. return label cost:{' '}
                  <span className="font-mono font-semibold text-text">
                    {formatCurrency(Number(rma.estimatedLabelCost ?? 0))}
                  </span>
                </p>
              </div>
              {canManage && (
                <div className="flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    loading={reviewMutation.isPending}
                    onClick={() =>
                      reviewMutation.mutate({ id: rma.id, action: 'approve-with-label' })
                    }
                  >
                    Approve & Buy Label
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={reviewMutation.isPending}
                    onClick={() =>
                      reviewMutation.mutate({ id: rma.id, action: 'approve-without-label' })
                    }
                  >
                    Approve without Label
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    loading={reviewMutation.isPending}
                    onClick={() => reviewMutation.mutate({ id: rma.id, action: 'deny' })}
                  >
                    Deny & Close
                  </Button>
                </div>
              )}
            </div>
            {(rma.evidenceUrls?.length ?? 0) > 0 && (
              <div className="mt-3">
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
                  Evidence photos
                </p>
                <div className="flex flex-wrap gap-2">
                  {rma.evidenceUrls!.map((url) => (
                    <AuthenticatedImage
                      key={url}
                      src={url}
                      alt="RMA evidence"
                      className="h-24 w-24 rounded-md border border-border object-cover"
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </Card>
  );
}

function DispositionSelect({
  line,
  returnId,
}: {
  line: ReturnLine;
  returnId: string;
}) {
  const queryClient = useQueryClient();
  const sku = line.sku ?? line.id;

  const mutation = useMutation({
    mutationFn: async (disposition: string) => {
      await apiClient.put(`/api/v1/returns/${returnId}/lines/${line.id}`, {
        disposition,
        restockLocationId: line.restockLocationId,
        restockingFeePct: line.restockingFeePct ?? 0,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
  });

  return (
    <select
      data-testid={`rma-disposition-${sku}`}
      value={line.disposition ?? ''}
      onChange={(e) => mutation.mutate(e.target.value)}
      disabled={mutation.isPending}
      className="h-8 rounded-md border border-border bg-surface-raised px-2 text-sm text-text"
    >
      <option value="">Set disposition</option>
      {DISPOSITIONS.map((d) => (
        <option key={d} value={d}>
          {d}
        </option>
      ))}
    </select>
  );
}

function ReturnLinesTable({
  lines,
  returnId,
  locations,
  canDisposition,
  onInspect,
}: {
  lines: ReturnLine[];
  returnId: string;
  locations: TenantLocation[];
  canDisposition: boolean;
  onInspect: (line: ReturnLine) => void;
}) {
  const queryClient = useQueryClient();
  const { sort, toggle, sorted } = useClientSort(
    lines,
    {
      sku: (line) => line.sku ?? line.productName ?? line.id,
      expected: (line) => line.quantityExpected,
      received: (line) => line.quantityReceived,
      disposition: (line) => line.disposition ?? '',
    },
    { key: 'sku', dir: 'asc' },
  );

  const patchLine = useMutation({
    mutationFn: async (payload: {
      line: ReturnLine;
      restockLocationId?: string;
      restockingFeePct?: number;
    }) => {
      await apiClient.put(`/api/v1/returns/${returnId}/lines/${payload.line.id}`, {
        disposition: payload.restockLocationId
          ? 'RESTOCK'
          : payload.line.disposition || 'QUARANTINE',
        restockLocationId: payload.restockLocationId ?? payload.line.restockLocationId,
        restockingFeePct: payload.restockingFeePct ?? payload.line.restockingFeePct ?? 0,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
  });

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead sortable sortKey="sku" sort={sort} onSort={toggle}>
            SKU
          </TableHead>
          <TableHead sortable sortKey="expected" sort={sort} onSort={toggle}>
            Expected
          </TableHead>
          <TableHead sortable sortKey="received" sort={sort} onSort={toggle}>
            Received
          </TableHead>
          <TableHead sortable sortKey="disposition" sort={sort} onSort={toggle}>
            Disposition
          </TableHead>
          <TableHead>Restock Bin</TableHead>
          <TableHead>Fee %</TableHead>
          <TableHead align="right">QC</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sorted.map((line) => {
          const sku = line.sku ?? line.id;
          const restock = line.disposition === 'RESTOCK';
          return (
            <TableRow key={line.id}>
              <TableCell mono>{line.sku ?? line.productName ?? line.id}</TableCell>
              <TableCell mono>{line.quantityExpected}</TableCell>
              <TableCell mono>{line.quantityReceived}</TableCell>
              <TableCell>
                {canDisposition ? (
                  <DispositionSelect line={line} returnId={returnId} />
                ) : (
                  <span className="text-sm text-text-muted">{line.disposition ?? '—'}</span>
                )}
              </TableCell>
              <TableCell>
                <select
                  data-testid={`rma-restock-bin-${sku}`}
                  disabled={!canDisposition || !restock || patchLine.isPending}
                  value={line.restockLocationId ?? ''}
                  onChange={(e) =>
                    patchLine.mutate({ line, restockLocationId: e.target.value })
                  }
                  aria-invalid={restock && !line.restockLocationId}
                  className={cn(
                    'h-8 max-w-[12rem] rounded-md border bg-surface-raised px-2 text-sm text-text disabled:opacity-50',
                    restock && !line.restockLocationId ? 'border-danger' : 'border-border',
                  )}
                >
                  <option value="">Target bin…</option>
                  {locations.map((loc) => (
                    <option key={loc.id} value={loc.id}>
                      {loc.path || loc.code || loc.name}
                    </option>
                  ))}
                </select>
              </TableCell>
              <TableCell>
                <Input
                  data-testid={`rma-restock-fee-${sku}`}
                  type="number"
                  min="0"
                  max="100"
                  step="0.1"
                  className="w-20"
                  disabled={!canDisposition}
                  defaultValue={line.restockingFeePct ?? 0}
                  onBlur={(e) =>
                    patchLine.mutate({
                      line,
                      restockingFeePct: Number(e.target.value || 0),
                    })
                  }
                />
              </TableCell>
              <TableCell align="right">
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  data-testid={`inspect-line-${line.id}`}
                  onClick={() => onInspect(line)}
                >
                  <ClipboardCheck className="h-4 w-4" />
                  Inspect / QC Details
                </Button>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

function AccordionSkeleton() {
  return (
    <div className="space-y-3" aria-hidden>
      {[0, 1].map((i) => (
        <div
          key={i}
          className="h-20 animate-pulse rounded-lg border border-border bg-surface-overlay"
        />
      ))}
    </div>
  );
}

export function ReturnsPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const canManage = useSessionStore((s) => s.canManageInventory());
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [inspectLine, setInspectLine] = useState<ReturnLine | null>(null);
  const [inspectReturnId, setInspectReturnId] = useState<string | null>(null);
  const isMobile = useMediaQuery('(max-width: 767px)');

  const { data: locations = [] } = useQuery({
    queryKey: ['locations'],
    queryFn: async () => (await apiClient.get<TenantLocation[]>('/api/v1/locations')).data,
    retry: false,
  });

  const table = useServerTableQuery<Return>({
    queryKey: ['returns', 'page'],
    path: '/api/v1/returns',
    defaultSort: 'createdAt,desc',
    extraParams: { status: statusFilter !== 'ALL' ? statusFilter : undefined },
    fetcher: listReturns,
  });
  const {
    items: returns,
    isLoading,
    isError,
    error,
    refetch,
  } = table;
  useCapMobilePageSize(isMobile, table.size, table.setSize);

  const completeMutation = useMutation({
    mutationFn: async (id: string) =>
      (await apiClient.post<Return>(`/api/v1/returns/${id}/complete`)).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
    },
    onError: (error) => {
      toast(rmaActionErrorMessage(error), { tone: 'danger' });
    },
  });

  const escalateMutation = useMutation({
    mutationFn: async (id: string) =>
      (await apiClient.post<{ rtvOrderId: string }>(`/api/v1/returns/${id}/escalate-rtv`)).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
      navigate('/purchasing/rtv');
    },
    onError: (error) => {
      toast(rmaActionErrorMessage(error), { tone: 'danger' });
    },
  });

  return (
    <TableDensityScope gridId="returns">
    <div className="p-6" data-testid="returns-page">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Returns (RMA)</h1>
          <p className="mt-1 text-sm text-text-muted">Manage return authorizations and dispositions</p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => navigate('/returns/receive')}>
            Receive terminal
          </Button>
          {canManage && (
            <Button data-testid="new-rma-button" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" />
              New RMA
            </Button>
          )}
        </div>
      </div>

      <RmaReviewQueue canManage={canManage} />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
        {STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            onClick={() => {
              setStatusFilter(status);
              table.setPage(1);
            }}
            className={cn(
              'rounded-full px-3 py-1 text-sm font-medium transition-colors',
              statusFilter === status
                ? 'bg-accent text-text-inverse'
                : 'bg-surface-overlay text-text-muted hover:text-text'
            )}
          >
            {status === 'ALL' ? 'All' : status}
          </button>
        ))}
        </div>
        <DensityToggle gridId="returns" />
      </div>

      {isLoading && <AccordionSkeleton />}
      {isError && (
        <Card className="p-6">
          <p className="text-sm text-danger">
            {(error as Error | undefined)?.message ?? 'Could not load returns.'}
          </p>
          <Button className="mt-3" variant="secondary" onClick={() => void refetch()}>
            Retry
          </Button>
        </Card>
      )}
      {!isLoading && !isError && returns.length === 0 && (
        <Card className="flex flex-col items-center gap-3 p-10 text-center">
          <RotateCcw className="h-8 w-8 text-text-muted" />
          <h2 className="text-lg font-semibold text-text">No returns</h2>
          <p className="text-sm text-text-muted">
            Return requests will appear here for approval and processing.
          </p>
          {canManage && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" />
              Create RMA
            </Button>
          )}
        </Card>
      )}

      <div
        className="space-y-4"
        data-testid={isMobile ? 'returns-mobile-list' : 'returns-desktop-list'}
      >
        {returns.map((rma) => {
          const itemCount = rma.itemCount ?? rma.lines?.length ?? 0;
          const estimated = Number(rma.estimatedReturnValue ?? 0);
          const canDisposition = canManage && DISPOSITION_STATUSES.has(rma.status);
          const completeBlocked = restockLinesMissingBin(rma.lines ?? []);
          return (
            <Card key={rma.id} padding="none" data-testid={`rma-card-${rma.number}`}>
              <button
                type="button"
                className="flex w-full flex-col items-start gap-3 p-4 text-left hover:bg-surface-overlay sm:flex-row sm:items-center sm:justify-between"
                onClick={() => setExpandedId(expandedId === rma.id ? null : rma.id)}
              >
                <div className="flex min-w-0 items-center gap-4">
                  <span className="font-mono font-semibold text-text">{rma.number}</span>
                  <span className="truncate text-sm text-text-muted">
                    {rma.customerName ?? rma.salesOrderNumber ?? rma.salesOrderId}
                  </span>
                  <span
                    className={cn(
                      'rounded-full px-2 py-0.5 text-xs font-medium',
                      STATUS_STYLES[rma.status] ?? 'bg-surface-overlay text-text-muted'
                    )}
                  >
                    {rma.status}
                  </span>
                </div>
                <div className="flex flex-wrap items-center justify-end gap-3 text-sm text-text-muted">
                  <span>{formatMediumDate(rma.createdAt)}</span>
                  <span>
                    {itemCount} item{itemCount === 1 ? '' : 's'} · {formatCurrency(estimated)}{' '}
                    estimated return value
                  </span>
                  {rma.trackingNumber && (
                    <span className="rounded-full bg-accent-muted px-2 py-0.5 font-mono text-xs text-accent">
                      {rma.trackingNumber}
                    </span>
                  )}
                  {rma.returnLabelUrl && (
                    <a
                      href={rma.returnLabelUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="flex items-center gap-1 text-accent hover:underline"
                    >
                      <Download className="h-4 w-4" />
                      Download Inbound Label
                    </a>
                  )}
                </div>
              </button>

              {expandedId === rma.id && (rma.lines?.length ?? 0) > 0 && (
                <div className="border-t border-border p-4">
                  <CardHeader title="Line items" description="Set disposition per line" />
                  <ReturnLinesTable
                    lines={rma.lines ?? []}
                    returnId={rma.id}
                    locations={locations}
                    canDisposition={canDisposition}
                    onInspect={(line) => {
                      setInspectReturnId(rma.id);
                      setInspectLine(line);
                    }}
                  />
                  {rma.creditMemoId && (
                    <p className="mt-3 text-sm">
                      Credit memo:{' '}
                      <button
                        type="button"
                        data-testid="rma-credit-memo-link"
                        className="font-mono text-accent hover:underline"
                        onClick={() => navigate(`/invoices/${rma.creditMemoId}`)}
                      >
                        {rma.creditMemoNumber ?? rma.creditMemoId}
                      </button>
                    </p>
                  )}
                  {canDisposition && (
                    <div className="mt-4 flex flex-wrap gap-2">
                      <Button
                        data-testid="rma-complete-disposition"
                        loading={completeMutation.isPending}
                        disabled={completeBlocked}
                        onClick={() => completeMutation.mutate(rma.id)}
                      >
                        Complete Disposition & Close RMA
                      </Button>
                      <Button
                        variant="secondary"
                        data-testid="rma-escalate-rtv"
                        loading={escalateMutation.isPending}
                        onClick={() => escalateMutation.mutate(rma.id)}
                      >
                        Escalate to RTV
                      </Button>
                    </div>
                  )}
                </div>
              )}
            </Card>
          );
        })}
      </div>

      {!isLoading && !isError && (
        <Pagination
          page={table.page}
          totalPages={table.totalPages}
          totalElements={table.totalElements}
          size={table.size}
          onPageChange={table.setPage}
          onSizeChange={table.setSize}
        />
      )}

      <NewRmaWizard open={createOpen} onClose={() => setCreateOpen(false)} />
      <RmaInspectionDrawer
        open={inspectLine != null && inspectReturnId != null}
        onClose={() => {
          setInspectLine(null);
          setInspectReturnId(null);
        }}
        returnId={inspectReturnId ?? ''}
        line={inspectLine}
      />
    </div>
    </TableDensityScope>
  );
}
