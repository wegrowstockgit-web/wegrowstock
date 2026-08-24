import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { ShippingCredentialStatus } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/Toast';
import { extractApiError } from '@/lib/apiClient';
import { LiveConnectionBadge } from '@/features/settings/LiveConnectionBadge';

const CARRIERS = ['EASYPOST', 'UPS', 'FEDEX'] as const;

export function CarrierCredentials() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [shippingSystem, setShippingSystem] = useState<string>('EASYPOST');
  const [shippingKey, setShippingKey] = useState('');
  const [error, setError] = useState('');

  const { data: shippingAccounts = [] } = useQuery({
    queryKey: ['shipping-accounts'],
    queryFn: async () =>
      (await apiClient.get<ShippingCredentialStatus[]>('/api/v1/settings/shipping-accounts')).data,
    retry: false,
  });

  const saveShippingMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/shipping-accounts', {
        system: shippingSystem,
        apiKey: shippingKey,
      });
    },
    onSuccess: () => {
      setShippingKey('');
      setError('');
      void queryClient.invalidateQueries({ queryKey: ['shipping-accounts'] });
    },
    onError: (err) => {
      setError(extractApiError(err, 'Could not save the shipping credential.'));
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: async (system: string) => {
      await apiClient.delete(`/api/v1/settings/shipping-accounts/${system}`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['shipping-accounts'] }),
    onError: (err) => {
      toast(extractApiError(err, 'Could not disconnect the shipping account.'), { tone: 'danger' });
    },
  });

  return (
    <Card data-testid="carrier-credentials">
      <CardHeader title="Shipping accounts" description="Carrier credentials for label generation" />
      <form
        className="mb-4 grid gap-4 sm:grid-cols-3"
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          saveShippingMutation.mutate();
        }}
      >
        <Select
          label="Carrier"
          value={shippingSystem}
          onChange={(e) => setShippingSystem(e.target.value)}
        >
          {CARRIERS.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </Select>
        <Input
          label="API key"
          type="password"
          value={shippingKey}
          onChange={(e) => setShippingKey(e.target.value)}
          required
          autoComplete="off"
        />
        <div className="flex items-end">
          <Button type="submit" loading={saveShippingMutation.isPending}>
            Save credential
          </Button>
        </div>
        {error && <p className="sm:col-span-3 text-sm text-danger">{error}</p>}
      </form>
      <div className="flex flex-col gap-2">
        {shippingAccounts.map((account) => {
          const live = account.status === 'CONNECTED';
          return (
            <div key={account.system} className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-medium text-text">{account.system}</span>
              {live ? (
                <>
                  <LiveConnectionBadge />
                  <Button
                    variant="ghost"
                    size="sm"
                    loading={disconnectMutation.isPending}
                    onClick={() => disconnectMutation.mutate(account.system)}
                  >
                    Disconnect Link
                  </Button>
                </>
              ) : (
                <span className="text-xs text-text-muted">Not configured</span>
              )}
            </div>
          );
        })}
      </div>
    </Card>
  );
}
