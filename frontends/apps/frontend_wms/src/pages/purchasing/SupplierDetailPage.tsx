import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, PurchaseOrder, Supplier } from '@/api/types';
import { RequireRole } from '@/components/auth/RequireRole';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';
import { useSessionStore } from '@/stores/session';

type Tab = 'pos' | 'rtv';

type RtvRow = {
  id: string;
  number: string;
  supplierId?: string;
  status: string;
  totalChargebackAmount?: number;
  debitMemoNumber?: string;
};

function addressFromContact(supplier: Supplier | undefined): Record<string, string> {
  const raw = supplier?.contact?.address;
  if (raw && typeof raw === 'object') {
    return raw as Record<string, string>;
  }
  return {};
}

export function SupplierDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const canEdit = useSessionStore((s) => s.hasRole('BUYER', 'ADMIN', 'OWNER', 'WAREHOUSE_MANAGER'));
  const [tab, setTab] = useState<Tab>('pos');

  const supplierQuery = useQuery({
    queryKey: ['suppliers', id],
    queryFn: async () => (await apiClient.get<Supplier>(`/api/v1/suppliers/${id}`)).data,
    enabled: !!id,
  });

  const poQuery = useQuery({
    queryKey: ['purchase-orders', 'by-supplier', id],
    queryFn: async () => (await apiClient.get<PaginatedResponse<PurchaseOrder>>('/api/v1/purchase-orders?size=200')).data,
    enabled: !!id,
  });

  const rtvQuery = useQuery({
    queryKey: ['rtv', 'by-supplier', id],
    queryFn: async () => (await apiClient.get<RtvRow[]>('/api/v1/rtv')).data,
    enabled: !!id,
  });

  const supplier = supplierQuery.data;
  const [name, setName] = useState('');
  const [paymentTerms, setPaymentTerms] = useState('NET30');
  const [leadTime, setLeadTime] = useState('');
  const [line1, setLine1] = useState('');
  const [city, setCity] = useState('');

  useEffect(() => {
    if (!supplier) return;
    const nextAddress = addressFromContact(supplier);
    setName(supplier.name);
    setPaymentTerms(supplier.paymentTerms ?? 'NET30');
    setLeadTime(supplier.defaultLeadTimeDays != null ? String(supplier.defaultLeadTimeDays) : '');
    setLine1(nextAddress.line1 ?? nextAddress.street ?? '');
    setCity(nextAddress.city ?? '');
  }, [supplier]);

  const pos = useMemo(
    () => (poQuery.data?.items ?? []).filter((po) => !id || po.supplierId === id),
    [poQuery.data, id],
  );
  const rtvs = useMemo(
    () => (rtvQuery.data ?? []).filter((row) => !id || row.supplierId === id),
    [rtvQuery.data, id],
  );

  const saveMutation = useMutation({
    mutationFn: async () =>
      apiClient.patch(`/api/v1/suppliers/${id}`, {
        name,
        paymentTerms,
        defaultLeadTimeDays: leadTime ? Number(leadTime) : null,
        contactEmail: supplier?.contactEmail,
        address: { line1, city },
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      toast('Supplier master data saved.', { tone: 'success' });
    },
    onError: () => toast('Could not save supplier master data.', { tone: 'danger' }),
  });

  if (supplierQuery.isLoading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-text-muted" data-testid="supplier-workspace-loading">
        Loading supplier…
      </div>
    );
  }

  if (supplierQuery.isError || !supplier) {
    return (
      <div className="space-y-4 p-6" data-testid="supplier-workspace-error">
        <p className="text-sm text-danger">This supplier could not be loaded.</p>
        <Button variant="secondary" onClick={() => navigate('/suppliers')}>
          Back to suppliers
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="supplier-workspace">
      <header className="shrink-0 border-b border-border/60 px-6 py-4">
        <Link
          to="/suppliers"
          className="inline-flex items-center gap-1.5 text-sm text-text-muted transition-colors hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          Suppliers
        </Link>
        <h1 className="mt-3 text-2xl font-bold text-text" data-testid="supplier-workspace-title">
          {supplier.name}
        </h1>
      </header>

      <div className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <div className="grid gap-6 lg:grid-cols-2">
          <section className="space-y-4 rounded-lg border border-border p-4">
            <h2 className="text-sm font-semibold text-text">Master data</h2>
            <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} disabled={!canEdit} />
            <Select
              label="Payment terms"
              value={paymentTerms}
              onChange={(e) => setPaymentTerms(e.target.value)}
              disabled={!canEdit}
            >
              <option value="NET30">NET30</option>
              <option value="NET60">NET60</option>
              <option value="DUE_ON_RECEIPT">Due on receipt</option>
            </Select>
            <Input
              label="Lead time (days)"
              type="number"
              min="0"
              value={leadTime}
              onChange={(e) => setLeadTime(e.target.value)}
              disabled={!canEdit}
            />
            <Input label="Address" value={line1} onChange={(e) => setLine1(e.target.value)} disabled={!canEdit} />
            <Input label="City" value={city} onChange={(e) => setCity(e.target.value)} disabled={!canEdit} />
            <RequireRole roles={['BUYER', 'ADMIN', 'OWNER', 'WAREHOUSE_MANAGER']}>
              <Button data-testid="save-supplier" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
                Save master data
              </Button>
            </RequireRole>
          </section>

          <section className="space-y-4 rounded-lg border border-border p-4">
            <div className="flex gap-2">
              <Button
                size="sm"
                variant={tab === 'pos' ? 'primary' : 'secondary'}
                data-testid="supplier-tab-pos"
                onClick={() => setTab('pos')}
              >
                Active POs
              </Button>
              <Button
                size="sm"
                variant={tab === 'rtv' ? 'primary' : 'secondary'}
                data-testid="supplier-tab-rtv"
                onClick={() => setTab('rtv')}
              >
                RTV & Chargebacks
              </Button>
            </div>

            {tab === 'pos' ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Number</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pos.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={2} className="text-text-muted">
                        No purchase orders for this supplier.
                      </TableCell>
                    </TableRow>
                  ) : (
                    pos.map((po) => (
                      <TableRow key={po.id}>
                        <TableCell mono>
                          <Link to={`/purchasing/orders/${po.id}`} className="text-accent hover:underline">
                            {po.number}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <span className={cn('text-sm text-text-muted')}>{po.status.replaceAll('_', ' ')}</span>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Number</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Debit memo</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rtvs.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={3} className="text-text-muted">
                        No RTV or chargeback history for this supplier.
                      </TableCell>
                    </TableRow>
                  ) : (
                    rtvs.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell mono>{row.number}</TableCell>
                        <TableCell>{row.status.replaceAll('_', ' ')}</TableCell>
                        <TableCell mono>{row.debitMemoNumber ?? '—'}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
