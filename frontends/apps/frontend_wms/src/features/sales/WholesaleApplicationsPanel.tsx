import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Inbox } from 'lucide-react';
import {
  approveWholesaleApplication,
  listWholesaleApplications,
  type WholesaleApplication,
} from '@/api/portal';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { UndoToast } from '@/components/ui/UndoToast';
import { useToast } from '@/components/ui/Toast';
import { useUndoToast } from '@/hooks/useUndoToast';
import { TableSkeleton } from '@/components/ui/Skeleton';

export function WholesaleApplicationsPanel() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const undo = useUndoToast();
  const [selected, setSelected] = useState<WholesaleApplication | null>(null);

  const { data = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['wholesale-applications', 'PENDING'],
    queryFn: () => listWholesaleApplications('PENDING'),
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => approveWholesaleApplication(id),
    onSuccess: (approved) => {
      setSelected(null);
      void queryClient.invalidateQueries({ queryKey: ['wholesale-applications'] });
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      toast('Welcome link sent', { tone: 'success' });
      undo.schedule({
        message: `${approved.companyName} approved — welcome link sent`,
        execute: () => undefined,
      });
    },
    onError: () => toast('Could not approve this application.', { tone: 'danger' }),
  });

  if (isLoading) {
    return <TableSkeleton rows={5} cols={4} />;
  }

  if (isError) {
    return (
      <EmptyState
        icon={Inbox}
        title="Unable to load applications"
        description="Refresh and try again."
        action={<Button onClick={() => refetch()}>Retry</Button>}
      />
    );
  }

  if (data.length === 0) {
    return (
      <EmptyState
        icon={Inbox}
        title="No pending applications"
        description="Wholesale applications submitted from the showroom will appear here."
      />
    );
  }

  return (
    <div data-testid="wholesale-applications-panel">
      <div className="min-w-0 w-full overflow-x-auto scrollbar-thin">
        <Table className="min-w-full table-auto">
          <TableHeader>
            <TableRow>
              <TableHead>Company</TableHead>
              <TableHead>Contact</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Tax ID</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.map((row) => (
              <TableRow
                key={row.id}
                className="cursor-pointer"
                onClick={() => setSelected(row)}
                data-testid={`wholesale-application-row-${row.id}`}
              >
                <TableCell>{row.companyName}</TableCell>
                <TableCell>{row.contactName}</TableCell>
                <TableCell>{row.email}</TableCell>
                <TableCell className="font-mono text-xs">{row.taxId}</TableCell>
                <TableCell>
                  <StatusBadge status={row.status} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <RightPeekDrawer
        open={!!selected}
        onClose={() => setSelected(null)}
        title={selected?.companyName ?? 'Application'}
        description="Verify tax ID before approving"
        width="lg"
      >
        {selected && (
          <div className="space-y-5" data-testid="wholesale-application-drawer">
            <dl className="grid grid-cols-1 gap-3 text-sm">
              <div>
                <dt className="text-text-muted">Company</dt>
                <dd className="font-medium text-text">{selected.companyName}</dd>
              </div>
              <div>
                <dt className="text-text-muted">Tax / VAT ID</dt>
                <dd className="font-mono text-text">{selected.taxId}</dd>
              </div>
              <div>
                <dt className="text-text-muted">Contact</dt>
                <dd className="text-text">{selected.contactName}</dd>
              </div>
              <div>
                <dt className="text-text-muted">Email</dt>
                <dd className="text-text">{selected.email}</dd>
              </div>
              <div>
                <dt className="text-text-muted">Phone</dt>
                <dd className="text-text">{selected.phone || '—'}</dd>
              </div>
            </dl>
            <Button
              size="lg"
              className="h-14 w-full text-base font-semibold"
              loading={approveMutation.isPending}
              data-testid="approve-welcome-link"
              onClick={() => approveMutation.mutate(selected.id)}
            >
              Approve & Send Welcome Link
            </Button>
          </div>
        )}
      </RightPeekDrawer>

      <UndoToast
        message={undo.message}
        visible={undo.visible}
        onUndo={undo.undo}
        onDismiss={undo.dismiss}
      />
    </div>
  );
}
