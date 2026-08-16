import { cn } from '@/lib/utils';

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-surface-overlay text-text-muted',
  DRAFT_QUOTE: 'bg-surface-overlay text-text-muted',
  PENDING_REP_APPROVAL: 'bg-warning/10 text-warning',
  QUOTE_READY: 'bg-accent-muted text-accent',
  QUOTE_ACCEPTED: 'bg-accent-muted text-accent',
  UNALLOCATED: 'bg-warning/10 text-warning',
  CONFIRMED: 'bg-accent-muted text-accent',
  PARTIALLY_ALLOCATED: 'bg-warning/10 text-warning',
  ALLOCATED: 'bg-accent-muted text-accent',
  BACKORDERED: 'bg-warning/10 text-warning',
  PARTIALLY_SHIPPED: 'bg-warning/10 text-warning',
  SHIPPED: 'bg-success/10 text-success',
  CLOSED: 'bg-success/10 text-success',
  CANCELLED: 'bg-danger/10 text-danger',
  OPEN: 'bg-accent-muted text-accent',
  PARTIALLY_PAID: 'bg-warning/10 text-warning',
  PAID: 'bg-success/10 text-success',
  VOID: 'bg-danger/10 text-danger',
  SUBMITTED: 'bg-accent-muted text-accent',
  RECEIVED: 'bg-success/10 text-success',
  PENDING: 'bg-surface-overlay text-text-muted',
  REQUESTED: 'bg-warning/10 text-warning',
  CONNECTED: 'bg-success/10 text-success',
  APPROVED: 'bg-success/10 text-success',
  REJECTED: 'bg-danger/10 text-danger',
};

interface StatusBadgeProps {
  status: string;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const style = STATUS_STYLES[status] ?? 'bg-surface-overlay text-text-muted';
  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
        style,
        className
      )}
    >
      {status.replaceAll('_', ' ')}
    </span>
  );
}
