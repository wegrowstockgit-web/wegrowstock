import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { Customer, CustomerBillingView, CustomerBillingSla } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { cn } from '@/lib/utils';
import { useSessionStore } from '@/stores/session';

type Tab = 'overview' | 'billing';

interface CustomerDetailProps {
  customer: Customer;
}

function formatMoney(amount: number | string | undefined) {
  return Number(amount ?? 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'USD',
  });
}

function SlaEditor({
  customerId,
  sla,
}: {
  customerId: string;
  sla: CustomerBillingSla | null | undefined;
}) {
  const queryClient = useQueryClient();
  const canEdit = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN'));
  const [storageMode, setStorageMode] = useState(sla?.storageMode ?? 'PALLET_POSITION');
  const [ratePerUnit, setRatePerUnit] = useState(String(sla?.ratePerUnit ?? '1.25'));
  const [pickFee, setPickFee] = useState(String(sla?.pickFeePerItem ?? '0.35'));
  const [message, setMessage] = useState('');

  useEffect(() => {
    setStorageMode(sla?.storageMode ?? 'PALLET_POSITION');
    setRatePerUnit(String(sla?.ratePerUnit ?? '1.25'));
    setPickFee(String(sla?.pickFeePerItem ?? '0.35'));
  }, [sla?.storageMode, sla?.ratePerUnit, sla?.pickFeePerItem]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.put<CustomerBillingSla>(
        `/api/v1/customers/${customerId}/billing/sla`,
        {
          storageMode,
          ratePerUnit: Number(ratePerUnit),
          pickFeePerItem: Number(pickFee),
        },
      );
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers', customerId, 'billing'] });
      setMessage('SLA saved.');
    },
    onError: () => setMessage('Could not save SLA.'),
  });

  return (
    <div className="space-y-3 rounded-md border border-border bg-surface-raised p-3">
      <p className="text-sm font-medium text-text">Active storage SLA</p>
      {!canEdit && (
        <dl className="grid gap-2 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-text-muted">Mode</dt>
            <dd className="font-medium text-text">{sla?.storageMode ?? '—'}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-text-muted">Rate / unit</dt>
            <dd className="font-mono">{formatMoney(sla?.ratePerUnit)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-text-muted">Pick fee / item</dt>
            <dd className="font-mono">{formatMoney(sla?.pickFeePerItem)}</dd>
          </div>
        </dl>
      )}
      {canEdit && (
        <>
          <Select
            label="Storage mode"
            value={storageMode}
            onChange={(e) => setStorageMode(e.target.value as CustomerBillingSla['storageMode'])}
          >
            <option value="PALLET_POSITION">Pallet / bin positions</option>
            <option value="CUBIC_VOLUME">Cubic volume</option>
          </Select>
          <Input
            label="Rate per unit ($)"
            type="number"
            min="0"
            step="0.01"
            value={ratePerUnit}
            onChange={(e) => setRatePerUnit(e.target.value)}
          />
          <Input
            label="Pick fee per item ($)"
            type="number"
            min="0"
            step="0.01"
            value={pickFee}
            onChange={(e) => setPickFee(e.target.value)}
          />
          <Button size="sm" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            Save SLA
          </Button>
          {message && (
            <p className={cn('text-xs', saveMutation.isError ? 'text-danger' : 'text-success')}>
              {message}
            </p>
          )}
        </>
      )}
      {!sla && !canEdit && (
        <p className="text-xs text-text-muted">No 3PL SLA configured for this customer.</p>
      )}
    </div>
  );
}

export function CustomerDetail({ customer }: CustomerDetailProps) {
  const [tab, setTab] = useState<Tab>('overview');

  const { data: billing, isLoading: billingLoading } = useQuery({
    queryKey: ['customers', customer.id, 'billing'],
    queryFn: async () =>
      (await apiClient.get<CustomerBillingView>(`/api/v1/customers/${customer.id}/billing`)).data,
    enabled: tab === 'billing',
    retry: false,
  });

  return (
    <div className="space-y-4" data-testid="customer-detail">
      <div className="flex gap-2 border-b border-border pb-2">
        <button
          type="button"
          className={cn(
            'rounded-md px-3 py-1.5 text-sm font-medium',
            tab === 'overview' ? 'bg-accent-muted text-accent' : 'text-text-muted hover:text-text',
          )}
          onClick={() => setTab('overview')}
        >
          Overview
        </button>
        <button
          type="button"
          className={cn(
            'rounded-md px-3 py-1.5 text-sm font-medium',
            tab === 'billing' ? 'bg-accent-muted text-accent' : 'text-text-muted hover:text-text',
          )}
          onClick={() => setTab('billing')}
          data-testid="customer-billing-tab"
        >
          3PL Billing
        </button>
      </div>

      {tab === 'overview' && (
        <dl className="space-y-3 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-text-muted">Name</dt>
            <dd className="font-medium text-text">{customer.name}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-text-muted">Email</dt>
            <dd>{customer.email ?? '—'}</dd>
          </div>
        </dl>
      )}

      {tab === 'billing' && (
        <div className="space-y-4">
          {billingLoading && <p className="text-sm text-text-muted">Loading billing…</p>}
          {!billingLoading && (
            <>
              <SlaEditor customerId={customer.id} sla={billing?.sla} />
              <div>
                <div className="mb-2 flex items-baseline justify-between gap-2">
                  <h3 className="text-sm font-semibold text-text">Unbilled accruals</h3>
                  <p className="font-mono text-sm font-semibold text-text">
                    {formatMoney(billing?.unbilledTotal)}
                  </p>
                </div>
                {(billing?.unbilledAccruals?.length ?? 0) === 0 ? (
                  <p className="text-sm text-text-muted">No unbilled storage accruals.</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Date</TableHead>
                        <TableHead>Description</TableHead>
                        <TableHead align="right">Amount</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {billing?.unbilledAccruals.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell>
                            {new Date(row.accrualDate + 'T00:00:00').toLocaleDateString()}
                          </TableCell>
                          <TableCell>{row.description}</TableCell>
                          <TableCell align="right" mono>
                            {formatMoney(row.amount)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
