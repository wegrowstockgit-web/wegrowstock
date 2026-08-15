import { Link } from 'react-router-dom';
import { AlertTriangle, ClipboardList, FileText, PackagePlus } from 'lucide-react';
import type { DashboardWorkQueue } from '@/api/types';
import { Card, CardHeader } from '@/components/ui/Card';
import { formatNumber } from '@/lib/utils';

interface WorkQueueProps {
  queue?: DashboardWorkQueue;
}

const ITEMS = [
  {
    key: 'needsAllocation' as const,
    label: 'Needs allocation',
    hint: 'Confirmed orders waiting for stock',
    to: '/sales-orders',
    icon: ClipboardList,
  },
  {
    key: 'readyToInvoice' as const,
    label: 'Ready to invoice',
    hint: 'Allocated or shipped — bill the customer',
    to: '/invoices',
    icon: FileText,
  },
  {
    key: 'unpaidInvoices' as const,
    label: 'Open AR',
    hint: 'Balances still outstanding',
    to: '/invoices',
    icon: FileText,
  },
  {
    key: 'lowStockItems' as const,
    label: 'Low stock',
    hint: 'Below reorder point',
    to: '/purchase-orders',
    icon: AlertTriangle,
  },
];

export function WorkQueue({ queue }: WorkQueueProps) {
  const total = queue
    ? queue.needsAllocation + queue.readyToInvoice + queue.unpaidInvoices + queue.lowStockItems
    : 0;

  return (
    <Card className="mb-6" padding="md" data-testid="work-queue">
      <CardHeader
        title="Do this next"
        description={
          total === 0
            ? 'Nothing urgent — you are caught up'
            : `${formatNumber(total)} items need attention`
        }
      />
      <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {ITEMS.map((item) => {
          const count = queue?.[item.key] ?? 0;
          const Icon = item.icon;
          return (
            <Link
              key={item.key}
              to={item.to}
              data-testid={`work-queue-${item.key}`}
              className="flex items-start gap-3 rounded-lg border border-border bg-surface px-3 py-3 transition-colors hover:border-accent/40 hover:bg-surface-overlay focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              <div className="rounded-md bg-accent-muted p-2 text-accent">
                <Icon className="h-4 w-4" aria-hidden />
              </div>
              <div className="min-w-0">
                <p className="text-2xl font-bold tabular-nums text-text">{formatNumber(count)}</p>
                <p className="text-sm font-medium text-text">{item.label}</p>
                <p className="text-xs text-text-muted">{item.hint}</p>
              </div>
            </Link>
          );
        })}
      </div>
      {total > 0 && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-text-muted">
          <PackagePlus className="h-3.5 w-3.5" aria-hidden />
          Open a queue to act — filters are already set up on each list
        </p>
      )}
    </Card>
  );
}
