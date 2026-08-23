import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { Customer } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { CustomerDetail } from '@/features/customers/CustomerDetail';
import { useSessionStore } from '@/stores/session';
import { formatCurrency } from '@/lib/utils';

export function CustomerWorkspacePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const hasRole = useSessionStore((s) => s.hasRole);
  const canInvite = hasRole('OWNER', 'ADMIN');
  const canHold = hasRole('FINANCE_ADMIN', 'OWNER', 'ADMIN');

  const { data: customer, isLoading } = useQuery({
    queryKey: ['customers', id],
    queryFn: async () => (await apiClient.get<Customer>(`/api/v1/customers/${id}`)).data,
    enabled: !!id,
  });

  const invite = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/customers/${id}/portal-invite`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });
  const hold = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/customers/${id}/credit-hold`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });

  return (
    <div className="mx-auto flex h-full max-w-5xl flex-col p-6" data-testid="customer-workspace-page">
      <button
        type="button"
        className="mb-2 flex items-center gap-1 text-sm text-text-muted hover:text-text"
        onClick={() => navigate('/customers')}
      >
        <ArrowLeft className="h-4 w-4" />
        Customers
      </button>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <h1 className="text-2xl font-bold text-text">{customer?.name ?? 'Customer'}</h1>
        <div className="flex gap-2">
          {canInvite && (
            <Button
              variant="secondary"
              disabled={!customer?.email || customer.portalStatus === 'ACTIVE'}
              loading={invite.isPending}
              onClick={() => invite.mutate()}
            >
              Send B2B Portal Invite
            </Button>
          )}
          {canHold && customer?.customerStatus !== 'HOLD' && (
            <Button variant="secondary" loading={hold.isPending} onClick={() => hold.mutate()}>
              Place on Credit Hold
            </Button>
          )}
        </div>
      </div>
      {isLoading || !customer ? (
        <p className="text-sm text-text-muted">Loading…</p>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-3 sm:grid-cols-3">
            <Card padding="sm">
              <p className="text-xs text-text-muted">Price tier</p>
              <p className="mt-1 font-medium">{customer.priceTierName || 'List'}</p>
            </Card>
            <Card padding="sm">
              <p className="text-xs text-text-muted">Credit</p>
              <p className="mt-1 font-mono text-sm">
                {formatCurrency(Number(customer.availableCredit ?? 0))} /{' '}
                {formatCurrency(Number(customer.creditLimit ?? 0))}
              </p>
            </Card>
            <Card padding="sm">
              <p className="text-xs text-text-muted">Portal</p>
              <p className="mt-1 font-medium">{customer.portalStatus ?? 'NOT_INVITED'}</p>
            </Card>
          </div>
          <CustomerDetail customer={customer} />
        </div>
      )}
    </div>
  );
}
