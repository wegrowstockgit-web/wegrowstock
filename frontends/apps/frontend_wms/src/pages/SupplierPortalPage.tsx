import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { Package, Printer } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { SupplierPortalLabel, SupplierPortalPo } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';

export function SupplierPortalPage() {
  const { token } = useParams<{ token: string }>();
  const [expectedDate, setExpectedDate] = useState('');

  const { data: po, isLoading, error, refetch } = useQuery({
    queryKey: ['supplier-portal', token],
    queryFn: async () => {
      const res = await apiClient.get<SupplierPortalPo>(`/api/v1/public/supplier-portal/po/${token}`);
      return res.data;
    },
    enabled: Boolean(token),
    retry: false,
  });

  const { data: labels = [] } = useQuery({
    queryKey: ['supplier-portal', token, 'labels'],
    queryFn: async () => {
      const res = await apiClient.get<SupplierPortalLabel[]>(
        `/api/v1/public/supplier-portal/po/${token}/labels`
      );
      return res.data;
    },
    enabled: Boolean(token) && Boolean(po),
    retry: false,
  });

  const updateDeliveryMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post(`/api/v1/public/supplier-portal/po/${token}/expected-delivery`, {
        expectedAt: new Date(expectedDate).toISOString(),
      });
    },
    onSuccess: () => void refetch(),
  });

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface p-6">
        <p className="text-text-muted">Loading purchase order...</p>
      </div>
    );
  }

  if (error || !po) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface p-6">
        <Card className="max-w-md p-6 text-center">
          <p className="text-danger">This supplier link is invalid or has expired.</p>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-surface p-6">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="text-center">
          <Package className="mx-auto h-10 w-10 text-accent" />
          <h1 className="mt-2 text-2xl font-bold text-text">Supplier Portal</h1>
          <p className="text-sm text-text-muted">PO {po.number} · {po.supplierName}</p>
        </div>

        <Card>
          <CardHeader title="Purchase order" description={`Status: ${po.status}`} />
          <ul className="divide-y divide-border">
            {po.lines.map((line) => (
              <li key={line.id} className="flex items-center justify-between py-3">
                <div>
                  <p className="font-medium text-text">{line.sku}</p>
                  <p className="font-mono text-xs text-text-muted">{line.barcode}</p>
                </div>
                <p className="tabular-nums text-text">Qty {line.qtyOrdered}</p>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <CardHeader title="Expected delivery" description="Confirm when goods will ship" />
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              updateDeliveryMutation.mutate();
            }}
          >
            <Input
              type="datetime-local"
              label="Delivery date"
              value={expectedDate}
              onChange={(e) => setExpectedDate(e.target.value)}
              required
            />
            <Button type="submit" loading={updateDeliveryMutation.isPending}>
              Submit delivery date
            </Button>
          </form>
        </Card>

        <Card>
          <CardHeader
            title="Receiving labels"
            description="Print barcodes before goods arrive at the dock"
            action={
              <Button variant="secondary" size="sm" onClick={() => window.print()}>
                <Printer className="h-4 w-4" />
                Print
              </Button>
            }
          />
          <ul className="space-y-2">
            {labels.map((label) => (
              <li key={label.barcode} className="rounded-lg border border-border p-3">
                <p className="font-mono text-lg font-bold text-text">{label.barcode}</p>
                <p className="text-sm text-text-muted">
                  {label.sku} · Qty {label.quantity} · {label.poNumber}
                </p>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  );
}
