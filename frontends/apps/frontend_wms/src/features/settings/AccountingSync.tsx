import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link2, RefreshCw } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { AccountMapping, SyncLog, UpdateAccountMapping } from '@/api/types';
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
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';
import { LiveConnectionBadge } from '@/features/settings/LiveConnectionBadge';
import { useEffect } from 'react';

const ACCOUNT_TYPES = ['INVENTORY_ASSET', 'COGS', 'SALES_REVENUE', 'TAX'] as const;
const SYSTEMS = ['QUICKBOOKS', 'XERO'] as const;

const SYNC_STATUS_STYLES: Record<string, string> = {
  SYNCED: 'bg-success/20 text-success',
  PENDING: 'bg-warning/20 text-warning',
  FAILED: 'bg-danger/20 text-danger',
  SKIPPED: 'bg-surface-overlay text-text-muted',
};

function statusChip(status: string) {
  return (
    <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', SYNC_STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted')}>
      {status}
    </span>
  );
}

interface VaultStatus {
  system: string;
  status: string;
  connected: boolean;
}

export function AccountingSync() {
  const queryClient = useQueryClient();
  const [draftMappings, setDraftMappings] = useState<UpdateAccountMapping[]>([]);
  const [apiKeys, setApiKeys] = useState<Record<string, string>>({ QUICKBOOKS: '', XERO: '' });

  const { data: vaultStatuses = [] } = useQuery({
    queryKey: ['settings', 'integration-credentials', 'accounting'],
    queryFn: async () =>
      (
        await apiClient.get<VaultStatus[]>('/api/v1/settings/integration-credentials', {
          params: { systems: 'QUICKBOOKS,XERO' },
        })
      ).data,
    retry: false,
  });

  const { data: mappings = [], isLoading: mappingsLoading } = useQuery({
    queryKey: ['integrations', 'accounting', 'mappings'],
    queryFn: async () =>
      (await apiClient.get<AccountMapping[]>('/api/v1/integrations/accounting/mappings')).data,
    retry: false,
  });

  const { data: syncLogs = [], isLoading: logsLoading, refetch: refetchLogs } = useQuery({
    queryKey: ['integrations', 'sync-logs'],
    queryFn: async () => (await apiClient.get<SyncLog[]>('/api/v1/integrations/sync-logs')).data,
    retry: false,
  });

  useEffect(() => {
    if (mappings.length > 0) {
      setDraftMappings(
        mappings.map((m) => ({
          system: m.system,
          accountType: m.accountType,
          externalAccountId: m.externalAccountId,
        })),
      );
    }
  }, [mappings]);

  const saveVaultMutation = useMutation({
    mutationFn: async ({ system, apiKey }: { system: string; apiKey: string }) => {
      await apiClient.post('/api/v1/settings/integration-credentials', { system, apiKey });
    },
    onSuccess: (_data, vars) => {
      setApiKeys((prev) => ({ ...prev, [vars.system]: '' }));
      void queryClient.invalidateQueries({ queryKey: ['settings', 'integration-credentials'] });
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: async (system: string) => {
      await apiClient.delete(`/api/v1/settings/integration-credentials/${system}`);
    },
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ['settings', 'integration-credentials'] }),
  });

  const saveMutation = useMutation({
    mutationFn: async (payload: UpdateAccountMapping[]) => {
      await apiClient.put('/api/v1/integrations/accounting/mappings/bulk', { mappings: payload });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['integrations', 'accounting', 'mappings'] });
    },
  });

  const retryMutation = useMutation({
    mutationFn: async (logId: string) => {
      await apiClient.post(`/api/v1/integrations/sync-logs/${logId}/retry`);
    },
    onSuccess: () => void refetchLogs(),
  });

  const updateMapping = (system: string, accountType: string, externalAccountId: string) => {
    setDraftMappings((prev) => {
      const existing = prev.find((m) => m.system === system && m.accountType === accountType);
      if (existing) {
        return prev.map((m) =>
          m.system === system && m.accountType === accountType ? { ...m, externalAccountId } : m,
        );
      }
      return [...prev, { system, accountType, externalAccountId }];
    });
  };

  const getMappingValue = (system: string, accountType: string) => {
    const fromDraft = draftMappings.find((m) => m.system === system && m.accountType === accountType);
    if (fromDraft) return fromDraft.externalAccountId;
    return (
      mappings.find((m) => m.system === system && m.accountType === accountType)?.externalAccountId ??
      ''
    );
  };

  const statusFor = (system: string) => vaultStatuses.find((s) => s.system === system);

  return (
    <div className="space-y-6" data-testid="accounting-sync">
      <Card>
        <CardHeader
          title="Accounting connections"
          description="Store QuickBooks / Xero API credentials in the encrypted vault"
        />
        <div className="space-y-4">
          {SYSTEMS.map((system) => {
            const status = statusFor(system);
            const connected = status?.connected === true;
            return (
              <div
                key={system}
                className="flex flex-col gap-3 rounded-md border border-border p-4 sm:flex-row sm:items-end"
              >
                <div className="min-w-[8rem]">
                  <p className="text-sm font-medium text-text">
                    {system === 'QUICKBOOKS' ? 'QuickBooks' : 'Xero'}
                  </p>
                  <div className="mt-1 flex items-center gap-2">
                    {connected ? <LiveConnectionBadge /> : (
                      <span className="text-xs text-text-muted">Not connected</span>
                    )}
                  </div>
                </div>
                {connected ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    loading={disconnectMutation.isPending}
                    onClick={() => disconnectMutation.mutate(system)}
                  >
                    Disconnect Link
                  </Button>
                ) : (
                  <>
                    <Input
                      label="API key / client secret"
                      type="password"
                      value={apiKeys[system] ?? ''}
                      onChange={(e) => setApiKeys((prev) => ({ ...prev, [system]: e.target.value }))}
                      autoComplete="off"
                    />
                    <Button
                      loading={saveVaultMutation.isPending}
                      disabled={!apiKeys[system]?.trim()}
                      onClick={() =>
                        saveVaultMutation.mutate({ system, apiKey: apiKeys[system].trim() })
                      }
                    >
                      <Link2 className="h-4 w-4" />
                      Save to vault
                    </Button>
                  </>
                )}
              </div>
            );
          })}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Account mappings"
          description="Map inventory accounts to your chart of accounts"
          action={
            <Button size="sm" loading={saveMutation.isPending} onClick={() => saveMutation.mutate(draftMappings)}>
              Save mappings
            </Button>
          }
        />
        {mappingsLoading ? (
          <TableSkeleton rows={4} cols={3} />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Account type</TableHead>
                <TableHead>External account ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {SYSTEMS.flatMap((system) =>
                ACCOUNT_TYPES.map((accountType) => (
                  <TableRow key={`${system}-${accountType}`}>
                    <TableCell>{system}</TableCell>
                    <TableCell>{accountType}</TableCell>
                    <TableCell>
                      <Input
                        value={getMappingValue(system, accountType)}
                        onChange={(e) => updateMapping(system, accountType, e.target.value)}
                        placeholder="External account ID"
                      />
                    </TableCell>
                  </TableRow>
                )),
              )}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card>
        <CardHeader
          title="Sync log"
          description="Recent integration sync attempts"
          action={
            <Button variant="ghost" size="sm" onClick={() => refetchLogs()}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
          }
        />
        {logsLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : syncLogs.length === 0 ? (
          <p className="text-sm text-text-muted">No sync activity yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Retries</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {syncLogs.map((log) => (
                <TableRow key={log.id}>
                  <TableCell>{log.system}</TableCell>
                  <TableCell>
                    <span className="text-text-muted">{log.entityType}</span>
                    {log.entityId ? (
                      <span className="ml-1 font-mono text-xs">{log.entityId.slice(0, 8)}</span>
                    ) : null}
                  </TableCell>
                  <TableCell>{statusChip(log.status)}</TableCell>
                  <TableCell mono>{log.retryCount}</TableCell>
                  <TableCell>
                    {log.status === 'FAILED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={retryMutation.isPending}
                        onClick={() => retryMutation.mutate(log.id)}
                      >
                        Retry
                      </Button>
                    )}
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
