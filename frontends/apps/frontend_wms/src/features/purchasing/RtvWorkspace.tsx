import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { useToast } from '@/components/ui/Toast';
import { useState } from 'react';
import { cn } from '@/lib/utils';

interface RtvOrder {
  id: string;
  number: string;
  status: string;
  supplierId: string;
  purchaseOrderId?: string | null;
  debitMemoNumber?: string | null;
  totalChargebackAmount?: number;
  carrier?: string | null;
  trackingNumber?: string | null;
  createdAt?: string;
}

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  APPROVED: 'bg-warning/15 text-warning',
  SHIPPED: 'bg-success/15 text-success',
  CREDITED: 'bg-accent-muted text-accent',
  CANCELLED: 'bg-danger/15 text-danger',
};

export function RtvWorkspace() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [shipCarrier, setShipCarrier] = useState<Record<string, string>>({});
  const [shipTracking, setShipTracking] = useState<Record<string, string>>({});

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['rtv-orders'],
    queryFn: async () => (await apiClient.get<RtvOrder[]>('/api/v1/rtv')).data,
  });

  const approveMutation = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/rtv/${id}/approve`),
    onSuccess: () => {
      toast('RTV approved', { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['rtv-orders'] });
    },
    onError: () => toast('Approve failed', { tone: 'danger' }),
  });

  const shipMutation = useMutation({
    mutationFn: async (id: string) =>
      apiClient.post(`/api/v1/rtv/${id}/ship`, {
        carrier: shipCarrier[id] || 'UPS',
        trackingNumber: shipTracking[id] || `TRK-${Date.now()}`,
      }),
    onSuccess: () => {
      toast('RTV shipped — debit memo staged', { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['rtv-orders'] });
    },
    onError: () => toast('Ship RTV failed', { tone: 'danger' }),
  });

  return (
    <div className="space-y-4 p-4 sm:p-6" data-testid="rtv-workspace">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-text">Return to Vendor</h1>
          <p className="mt-1 text-sm text-text-muted">
            Supplier chargebacks, debit memos, and outbound RTV shipments
          </p>
        </div>
        <Link to="/exceptions" className="text-sm font-medium text-accent hover:underline">
          Open exceptions →
        </Link>
      </div>

      <Card>
        <CardHeader title="Active RTV orders" description="Draft → approve → ship posts SHIP ledger + AP debit memo" />
        {isLoading ? (
          <p className="px-4 pb-4 text-sm text-text-muted">Loading…</p>
        ) : orders.length === 0 ? (
          <p className="px-4 pb-4 text-sm text-text-muted">
            No RTV orders yet. Initiate from an open exception.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Number</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Debit memo</TableHead>
                <TableHead>Chargeback</TableHead>
                <TableHead>Tracking</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {orders.map((o) => (
                <TableRow key={o.id} data-testid={`rtv-row-${o.number}`}>
                  <TableCell className="font-medium">{o.number}</TableCell>
                  <TableCell>
                    <span
                      className={cn(
                        'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                        STATUS_STYLES[o.status] ?? 'bg-surface-overlay text-text-muted',
                      )}
                    >
                      {o.status}
                    </span>
                  </TableCell>
                  <TableCell mono className="text-xs">
                    {o.debitMemoNumber ?? '—'}
                  </TableCell>
                  <TableCell>
                    ${Number(o.totalChargebackAmount ?? 0).toFixed(2)}
                  </TableCell>
                  <TableCell className="text-sm text-text-muted">
                    {o.trackingNumber ? `${o.carrier ?? ''} ${o.trackingNumber}` : '—'}
                  </TableCell>
                  <TableCell align="right">
                    <div className="flex flex-wrap items-center justify-end gap-2">
                      {o.status === 'DRAFT' && (
                        <Button
                          size="sm"
                          data-testid={`rtv-approve-${o.id}`}
                          loading={approveMutation.isPending}
                          onClick={() => approveMutation.mutate(o.id)}
                        >
                          Approve
                        </Button>
                      )}
                      {(o.status === 'APPROVED' || o.status === 'DRAFT') && (
                        <>
                          <Input
                            className="h-9 w-24"
                            placeholder="Carrier"
                            value={shipCarrier[o.id] ?? ''}
                            onChange={(e) =>
                              setShipCarrier((m) => ({ ...m, [o.id]: e.target.value }))
                            }
                          />
                          <Input
                            className="h-9 w-32"
                            placeholder="Tracking"
                            value={shipTracking[o.id] ?? ''}
                            onChange={(e) =>
                              setShipTracking((m) => ({ ...m, [o.id]: e.target.value }))
                            }
                          />
                          <Button
                            size="sm"
                            variant="secondary"
                            data-testid={`rtv-ship-${o.id}`}
                            loading={shipMutation.isPending}
                            onClick={() => shipMutation.mutate(o.id)}
                          >
                            Ship
                          </Button>
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
