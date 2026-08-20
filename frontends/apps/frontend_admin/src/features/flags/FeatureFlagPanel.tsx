import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { FeatureFlagDto, TenantFeatureFlagOverrideDto } from '@invsys/shared-types';
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
import { PageHeader } from '@/features/layout/PageHeader';
import { createFeatureFlag, fetchFeatureFlags, putFeatureFlagTenants } from './api';

function parseTenantIds(raw: string): string[] {
  return raw
    .split(/[\s,]+/)
    .map((id) => id.trim())
    .filter(Boolean);
}

export function FeatureFlagPanel() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [flagKey, setFlagKey] = useState('');
  const [description, setDescription] = useState('');
  const [isGlobal, setIsGlobal] = useState(false);
  const [targetDrafts, setTargetDrafts] = useState<Record<string, string>>({});

  const { data: flags = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'flags'],
    queryFn: fetchFeatureFlags,
  });

  const createMutation = useMutation({
    mutationFn: () =>
      createFeatureFlag({
        flagKey: flagKey.trim(),
        description: description.trim() || undefined,
        isGlobal,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'flags'] });
      setFlagKey('');
      setDescription('');
      setIsGlobal(false);
      toast.success('Feature flag created');
    },
    onError: () => {
      toast.danger('Could not create feature flag.');
    },
  });

  const saveMutation = useMutation({
    mutationFn: ({
      id,
      nextGlobal,
      overrides,
    }: {
      id: string;
      nextGlobal: boolean;
      overrides: TenantFeatureFlagOverrideDto[];
    }) => putFeatureFlagTenants(id, { isGlobal: nextGlobal, overrides }),
    onSuccess: (updated) => {
      queryClient.setQueryData<FeatureFlagDto[]>(
        ['control-plane', 'flags'],
        (prev) => (prev ?? []).map((row) => (row.id === updated.id ? updated : row)),
      );
      toast.success(`Updated ${updated.flagKey}`);
    },
    onError: () => {
      toast.danger('Could not update feature flag targeting.');
    },
  });

  const onCreate = (e: FormEvent) => {
    e.preventDefault();
    if (!flagKey.trim()) return;
    createMutation.mutate();
  };

  const rows = useMemo(
    () => [...flags].sort((a, b) => a.flagKey.localeCompare(b.flagKey)),
    [flags],
  );

  if (isLoading) {
    return (
      <div data-testid="feature-flags">
        <PageSkeleton label="Loading feature flags…" />
      </div>
    );
  }

  if (isError) {
    return (
      <div data-testid="feature-flags">
        <p className="text-sm text-danger">Failed to load feature flags.</p>
      </div>
    );
  }

  return (
    <div className="space-y-8" data-testid="feature-flags">
      <PageHeader
        title="Feature Flags"
        description="Progressive delivery: global release or tenant-targeted beta tests."
      />

      <form className="admin-card space-y-4 p-5" onSubmit={onCreate} data-testid="flag-create-form">
        <h3 className="text-sm font-semibold text-text">Create flag</h3>
        <label className="block text-sm">
          <span className="text-text-muted">Key</span>
          <input
            value={flagKey}
            onChange={(e) => setFlagKey(e.target.value)}
            className="admin-field mt-1"
            placeholder="new-picking-wave"
            required
            maxLength={64}
            data-testid="flag-key-input"
          />
        </label>
        <label className="block text-sm">
          <span className="text-text-muted">Description</span>
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="admin-field mt-1"
            placeholder="What this flag unlocks"
          />
        </label>
        <label className="inline-flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="admin-switch"
            checked={isGlobal}
            onChange={(e) => setIsGlobal(e.target.checked)}
            data-testid="flag-global-create"
          />
          Global release
        </label>
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="rounded border border-border px-3 py-1.5 text-sm font-medium hover:bg-surface disabled:opacity-50"
        >
          Create flag
        </button>
      </form>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Flag</TableHead>
            <TableHead>Global</TableHead>
            <TableHead>Beta tenants</TableHead>
            <TableHead>Target</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={4} className="text-text-muted">
                No feature flags yet.
              </TableCell>
            </TableRow>
          ) : (
            rows.map((flag) => {
              const draft = targetDrafts[flag.id] ?? '';
              const tenants = flag.tenants ?? [];
              return (
                <TableRow key={flag.id} data-testid={`flag-row-${flag.flagKey}`}>
                  <TableCell>
                    <p className="font-mono text-sm">{flag.flagKey}</p>
                    <p className="text-xs text-text-muted">{flag.description || '—'}</p>
                  </TableCell>
                  <TableCell>
                    <label className="inline-flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        className="admin-switch"
                        checked={flag.isGlobal}
                        onChange={(e) =>
                          saveMutation.mutate({
                            id: flag.id,
                            nextGlobal: e.target.checked,
                            overrides: tenants,
                          })
                        }
                        data-testid={`flag-global-${flag.flagKey}`}
                      />
                      {flag.isGlobal ? 'On' : 'Off'}
                    </label>
                  </TableCell>
                  <TableCell className="max-w-xs">
                    {tenants.length === 0 ? (
                      <span className="text-text-muted">None</span>
                    ) : (
                      <ul className="space-y-1 font-mono text-xs text-text-muted">
                        {tenants.map((row) => (
                          <li key={row.tenantId}>
                            {row.tenantId} · {row.enabled ? 'on' : 'off'}
                          </li>
                        ))}
                      </ul>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex min-w-[16rem] flex-col gap-2">
                      <input
                        value={draft}
                        onChange={(e) =>
                          setTargetDrafts((prev) => ({ ...prev, [flag.id]: e.target.value }))
                        }
                        className="admin-field"
                        placeholder="tenant UUID, comma-separated"
                        data-testid={`flag-tenants-${flag.flagKey}`}
                      />
                      <button
                        type="button"
                        disabled={saveMutation.isPending}
                        className="rounded border border-border px-3 py-1.5 text-sm font-medium hover:bg-surface disabled:opacity-50"
                        onClick={() => {
                          const ids = parseTenantIds(draft);
                          const overrides: TenantFeatureFlagOverrideDto[] =
                            ids.length === 0
                              ? []
                              : ids.map((tenantId) => ({ tenantId, enabled: true }));
                          saveMutation.mutate({
                            id: flag.id,
                            nextGlobal: flag.isGlobal,
                            overrides,
                          });
                        }}
                      >
                        Save targeting
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>
    </div>
  );
}
