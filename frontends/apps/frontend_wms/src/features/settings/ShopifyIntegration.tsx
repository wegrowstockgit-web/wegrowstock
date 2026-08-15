import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, RefreshCw } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { ChannelIntegration } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';
import { LiveConnectionBadge } from '@/features/settings/LiveConnectionBadge';

interface VaultStatus {
  system: string;
  status: string;
  connected: boolean;
}

function statusChip(status?: string) {
  const normalized = status ?? 'UNKNOWN';
  return (
    <span
      className={cn(
        'rounded-full px-2 py-0.5 text-xs font-medium',
        normalized === 'ACTIVE' || normalized === 'CONNECTED'
          ? 'bg-success/20 text-success'
          : normalized === 'PENDING'
            ? 'bg-warning/20 text-warning'
            : 'bg-surface-overlay text-text-muted',
      )}
    >
      {normalized}
    </span>
  );
}

function ConnectChannelModal({
  platform,
  onClose,
}: {
  platform: string | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [shopIdentifier, setShopIdentifier] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/integrations/channels', {
        platform,
        shopIdentifier,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] });
      setShopIdentifier('');
      onClose();
    },
    onError: () => setError('Could not connect. That shop may already be connected.'),
  });

  return (
    <Modal
      open={platform !== null}
      onClose={onClose}
      title={`Connect ${platform === 'SHOPIFY' ? 'Shopify' : 'Amazon'}`}
      description={
        platform === 'SHOPIFY'
          ? 'Enter your shop domain, e.g. my-store.myshopify.com'
          : 'Enter your Amazon seller ID'
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input
          label={platform === 'SHOPIFY' ? 'Shop domain' : 'Seller ID'}
          value={shopIdentifier}
          onChange={(e) => setShopIdentifier(e.target.value)}
          required
          autoFocus
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Connect
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function ShopifyIntegration() {
  const queryClient = useQueryClient();
  const [connectPlatform, setConnectPlatform] = useState<string | null>(null);
  const [shopifyKey, setShopifyKey] = useState('');

  const { data: vaultStatus } = useQuery({
    queryKey: ['settings', 'integration-credentials', 'shopify'],
    queryFn: async () => {
      const res = await apiClient.get<VaultStatus[]>('/api/v1/settings/integration-credentials', {
        params: { systems: 'SHOPIFY' },
      });
      return res.data[0];
    },
    retry: false,
  });

  const { data: channels = [], isLoading, refetch } = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: async () =>
      (await apiClient.get<ChannelIntegration[]>('/api/v1/integrations/channels')).data,
    retry: false,
  });

  const saveVaultMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/integration-credentials', {
        system: 'SHOPIFY',
        apiKey: shopifyKey,
      });
    },
    onSuccess: () => {
      setShopifyKey('');
      void queryClient.invalidateQueries({ queryKey: ['settings', 'integration-credentials'] });
    },
  });

  const disconnectVaultMutation = useMutation({
    mutationFn: async () => {
      await apiClient.delete('/api/v1/settings/integration-credentials/SHOPIFY');
    },
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['settings', 'integration-credentials'] }),
  });

  const disconnectMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/integrations/channels/${id}`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] }),
  });

  const vaultLive = vaultStatus?.connected === true;
  const shopifyChannels = channels.filter((c) => c.platform === 'SHOPIFY');

  return (
    <div className="space-y-6" data-testid="shopify-integration">
      <Card>
        <CardHeader
          title="Shopify API vault"
          description="Admin API token stored in the encrypted credential vault"
        />
        <div className="flex flex-wrap items-end gap-3">
          {vaultLive ? (
            <>
              <LiveConnectionBadge />
              <Button
                variant="ghost"
                size="sm"
                loading={disconnectVaultMutation.isPending}
                onClick={() => disconnectVaultMutation.mutate()}
              >
                Disconnect Link
              </Button>
            </>
          ) : (
            <>
              <Input
                label="Shopify Admin API access token"
                type="password"
                value={shopifyKey}
                onChange={(e) => setShopifyKey(e.target.value)}
                className="min-w-[16rem]"
                autoComplete="off"
              />
              <Button
                loading={saveVaultMutation.isPending}
                disabled={!shopifyKey.trim()}
                onClick={() => saveVaultMutation.mutate()}
              >
                Save to vault
              </Button>
            </>
          )}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Channel integrations"
          description="Connect Shopify and Amazon for multi-channel sync"
        />
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" onClick={() => setConnectPlatform('SHOPIFY')}>
            <Plus className="h-4 w-4" />
            Connect Shopify
          </Button>
          <Button variant="secondary" onClick={() => setConnectPlatform('AMAZON')}>
            <Plus className="h-4 w-4" />
            Connect Amazon
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Connected channels"
          description="Live health and sync status"
          action={
            <Button variant="ghost" size="sm" onClick={() => refetch()}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
          }
        />
        {isLoading ? (
          <TableSkeleton rows={4} cols={5} />
        ) : channels.length === 0 ? (
          <p className="text-sm text-text-muted">No channels connected yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Platform</TableHead>
                <TableHead>Shop / Seller</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Credential</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {channels.map((channel) => (
                <TableRow key={channel.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      {channel.platform}
                      {channel.platform === 'SHOPIFY' &&
                        channel.status === 'ACTIVE' &&
                        vaultLive && <LiveConnectionBadge />}
                    </div>
                  </TableCell>
                  <TableCell mono>{channel.shopIdentifier}</TableCell>
                  <TableCell>{statusChip(channel.status)}</TableCell>
                  <TableCell>{statusChip(channel.credentialStatus)}</TableCell>
                  <TableCell align="right">
                    {channel.status !== 'DISCONNECTED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={disconnectMutation.isPending}
                        onClick={() => disconnectMutation.mutate(channel.id)}
                      >
                        Disconnect Link
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {shopifyChannels.length > 0 && !vaultLive && (
          <p className="mt-3 text-xs text-text-muted">
            Channel connected — add a vault API token above for LIVE sync credentials.
          </p>
        )}
      </Card>

      <ConnectChannelModal platform={connectPlatform} onClose={() => setConnectPlatform(null)} />
    </div>
  );
}
