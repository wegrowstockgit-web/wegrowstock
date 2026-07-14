import { useQuery } from '@tanstack/react-query';
import { ClipboardList } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PriorityAudit } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { TableSkeleton } from '@/components/ui/Skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';

export function CycleCountsPage() {
  const { data: audits = [], isLoading } = useQuery({
    queryKey: ['cycle-counts', 'priority-audits'],
    queryFn: async () => {
      const res = await apiClient.get<PriorityAudit[]>('/api/v1/cycle-counts/priority-audits');
      return res.data;
    },
    refetchInterval: 30_000,
    retry: false,
  });

  return (
    <div className="flex min-h-full flex-col p-4 pb-8" data-theme="warehouse">
      <div className="mb-6 text-center">
        <ClipboardList className="mx-auto h-8 w-8 text-accent" />
        <h1 className="mt-2 text-2xl font-bold text-text">Cycle counts</h1>
        <p className="text-sm text-text-muted">Priority audits based on movement velocity</p>
      </div>

      <Card>
        <CardHeader
          title="Priority audits"
          description="Bins flagged by velocity or adjustment patterns — count these first"
        />
        {isLoading ? (
          <TableSkeleton rows={4} cols={3} />
        ) : audits.length === 0 ? (
          <p className="py-8 text-center text-sm text-text-muted">No priority audits right now.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Location</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Created</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {audits.map((audit) => (
                <TableRow key={audit.id}>
                  <TableCell mono>{audit.locationPath}</TableCell>
                  <TableCell>{audit.notes ?? 'Priority audit'}</TableCell>
                  <TableCell>{new Date(audit.createdAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
