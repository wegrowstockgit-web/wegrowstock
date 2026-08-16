import { useState, type FormEvent } from 'react';
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
import {
  activateComplianceBroadcast,
  createComplianceBroadcast,
  fetchComplianceBroadcasts,
} from './api';
import { PageHeader } from '@/features/layout/PageHeader';

export function GlobalCompliancePanel() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [category, setCategory] = useState('TAX');
  const [title, setTitle] = useState('');
  const [payloadText, setPayloadText] = useState('{}');

  const { data: broadcasts = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'compliance', 'broadcasts'],
    queryFn: fetchComplianceBroadcasts,
  });

  const createMutation = useMutation({
    mutationFn: () => {
      let payload: Record<string, unknown> = {};
      try {
        payload = JSON.parse(payloadText || '{}') as Record<string, unknown>;
      } catch {
        throw new Error('INVALID_JSON');
      }
      return createComplianceBroadcast({ category, title, payload });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['control-plane', 'compliance', 'broadcasts'],
      });
      setTitle('');
      setPayloadText('{}');
      toast.success('Broadcast created');
    },
    onError: (err: Error) => {
      toast.danger(
        err.message === 'INVALID_JSON'
          ? 'Payload must be valid JSON.'
          : 'Could not create broadcast.',
      );
    },
  });

  const activateMutation = useMutation({
    mutationFn: activateComplianceBroadcast,
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['control-plane', 'compliance', 'broadcasts'],
      });
      toast.success('Broadcast activated and fanned out to tenant settings');
    },
    onError: () => {
      toast.danger('Could not activate broadcast.');
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    createMutation.mutate();
  };

  return (
    <div className="space-y-8" data-testid="global-compliance">
      <PageHeader
        title="Global compliance"
        description="Platform-wide compliance broadcasts (tax schemes, lot rules) pushed to all tenants."
      />

      <form
        onSubmit={onSubmit}
        className="admin-card space-y-3 p-5"
      >
        <h3 className="text-sm font-semibold text-text">New broadcast</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Category</span>
            <input
              className="admin-field"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              required
              data-testid="compliance-category"
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-text-muted">Title</span>
            <input
              className="admin-field"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              data-testid="compliance-title"
            />
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block text-text-muted">Payload JSON</span>
            <textarea
              className="admin-field min-h-[88px] h-auto py-2 font-mono"
              value={payloadText}
              onChange={(e) => setPayloadText(e.target.value)}
              data-testid="compliance-payload"
            />
          </label>
        </div>
        <button
          type="submit"
          disabled={createMutation.isPending}
          className="rounded border border-accent bg-accent/15 px-3 py-2 text-sm font-medium text-accent disabled:opacity-50"
        >
          Create broadcast
        </button>
      </form>

      {isLoading ? (
        <PageSkeleton label="Loading broadcasts…" />
      ) : isError ? (
        <p className="text-sm text-danger">Failed to load compliance broadcasts.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Active</TableHead>
              <TableHead>Created</TableHead>
              <TableHead> </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {broadcasts.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-text-muted">
                  No broadcasts yet.
                </TableCell>
              </TableRow>
            ) : (
              broadcasts.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium">{row.title}</TableCell>
                  <TableCell className="text-text-muted">{row.category}</TableCell>
                  <TableCell>{row.active ? 'Yes' : 'No'}</TableCell>
                  <TableCell className="text-text-muted">
                    {new Date(row.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <button
                      type="button"
                      className="text-sm text-accent underline-offset-4 hover:underline disabled:opacity-50"
                      disabled={row.active || activateMutation.isPending}
                      onClick={() => activateMutation.mutate(row.id)}
                    >
                      Activate
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
