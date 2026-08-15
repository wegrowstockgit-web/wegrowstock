import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  PageSkeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useToast,
} from '@invsys/shared-ui';
import { fetchTenants } from '@/features/tenants/api';
import { fetchShards, putShard, type ShardRoute } from './api';

const emptyForm = {
  tenantId: '',
  shardKey: 'primary',
  jdbcUrl: '',
  auroraCluster: '',
  region: 'us-east-1',
  notes: '',
};

export function ShardRoutingPanel() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [form, setForm] = useState(emptyForm);

  const shardsQuery = useQuery({
    queryKey: ['control-plane', 'shards'],
    queryFn: fetchShards,
  });

  const tenantsQuery = useQuery({
    queryKey: ['control-plane', 'tenants'],
    queryFn: fetchTenants,
  });

  const saveMutation = useMutation({
    mutationFn: () =>
      putShard(form.tenantId, {
        shardKey: form.shardKey,
        jdbcUrl: form.jdbcUrl || null,
        auroraCluster: form.auroraCluster || null,
        region: form.region,
        notes: form.notes || null,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'shards'] });
      toast.success('Shard route saved');
    },
    onError: () => {
      toast.danger('Could not save shard route.');
    },
  });

  const edit = (row: ShardRoute) => {
    setForm({
      tenantId: row.tenantId,
      shardKey: row.shardKey ?? 'primary',
      jdbcUrl: row.jdbcUrl ?? '',
      auroraCluster: row.auroraCluster ?? '',
      region: row.region ?? 'us-east-1',
      notes: row.notes ?? '',
    });
  };

  useEffect(() => {
    if (!form.tenantId && (tenantsQuery.data?.length ?? 0) > 0) {
      setForm((f) => ({ ...f, tenantId: tenantsQuery.data![0].tenantId }));
    }
  }, [tenantsQuery.data, form.tenantId]);

  return (
    <div className="space-y-8" data-testid="shard-routing">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Shard routing</h2>
        <p className="mt-1 text-sm text-text-muted">
          Map tenants to JDBC / Aurora shard keys for multi-region data planes.
        </p>
      </div>

      <section className="space-y-3 rounded-lg border border-border bg-surface-raised p-4">
        <h3 className="text-sm font-semibold text-text">Upsert route</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Tenant</span>
            <select
              className="w-full rounded border border-border bg-surface px-3 py-2 text-sm"
              value={form.tenantId}
              onChange={(e) => setForm((f) => ({ ...f, tenantId: e.target.value }))}
            >
              {(tenantsQuery.data ?? []).map((t) => (
                <option key={t.tenantId} value={t.tenantId}>
                  {t.slug}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Shard key</span>
            <input
              className="w-full rounded border border-border bg-surface px-3 py-2 text-sm"
              value={form.shardKey}
              onChange={(e) => setForm((f) => ({ ...f, shardKey: e.target.value }))}
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Region</span>
            <input
              className="w-full rounded border border-border bg-surface px-3 py-2 text-sm"
              value={form.region}
              onChange={(e) => setForm((f) => ({ ...f, region: e.target.value }))}
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Aurora cluster</span>
            <input
              className="w-full rounded border border-border bg-surface px-3 py-2 text-sm"
              value={form.auroraCluster}
              onChange={(e) => setForm((f) => ({ ...f, auroraCluster: e.target.value }))}
            />
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block text-text-muted">JDBC URL</span>
            <input
              className="w-full rounded border border-border bg-surface px-3 py-2 font-mono text-sm"
              value={form.jdbcUrl}
              onChange={(e) => setForm((f) => ({ ...f, jdbcUrl: e.target.value }))}
            />
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block text-text-muted">Notes</span>
            <input
              className="w-full rounded border border-border bg-surface px-3 py-2 text-sm"
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
            />
          </label>
        </div>
        <button
          type="button"
          disabled={!form.tenantId || saveMutation.isPending}
          className="rounded border border-accent bg-accent/15 px-3 py-2 text-sm font-medium text-accent disabled:opacity-50"
          onClick={() => saveMutation.mutate()}
        >
          Save route
        </button>
      </section>

      {shardsQuery.isLoading ? (
        <PageSkeleton label="Loading shard routes…" />
      ) : shardsQuery.isError ? (
        <p className="text-sm text-danger">Failed to load shard routes.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Tenant</TableHead>
              <TableHead>Shard</TableHead>
              <TableHead>Region</TableHead>
              <TableHead>Aurora</TableHead>
              <TableHead> </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(shardsQuery.data ?? []).length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-text-muted">
                  No shard routes configured.
                </TableCell>
              </TableRow>
            ) : (
              (shardsQuery.data ?? []).map((row) => (
                <TableRow key={row.tenantId}>
                  <TableCell className="font-mono text-xs">{row.tenantId}</TableCell>
                  <TableCell className="font-medium">{row.shardKey}</TableCell>
                  <TableCell className="text-text-muted">{row.region}</TableCell>
                  <TableCell className="text-text-muted">{row.auroraCluster ?? '—'}</TableCell>
                  <TableCell>
                    <button
                      type="button"
                      className="text-sm text-accent underline-offset-4 hover:underline"
                      onClick={() => edit(row)}
                    >
                      Edit
                    </button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
