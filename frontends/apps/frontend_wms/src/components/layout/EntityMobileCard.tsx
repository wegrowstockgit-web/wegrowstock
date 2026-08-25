import type { KeyboardEvent, MouseEvent, ReactNode } from 'react';
import { cn } from '@/lib/utils';

/**
 * Compact touch card for phone list workspaces. Renders only the current
 * server page's rows — never an unbounded dataset.
 */
export function EntityMobileCard({
  identity,
  title,
  status,
  amount,
  date,
  onClick,
  footer,
  testId,
  className,
}: {
  identity: string;
  title: string;
  status?: ReactNode;
  amount?: string;
  date?: string;
  onClick?: () => void;
  footer?: ReactNode;
  testId?: string;
  className?: string;
}) {
  const interactive = typeof onClick === 'function';

  const handleActivate = (event: MouseEvent | KeyboardEvent) => {
    if (!onClick) return;
    if ('key' in event && event.key !== 'Enter' && event.key !== ' ') return;
    if ('key' in event) event.preventDefault();
    onClick();
  };

  return (
    <div
      data-testid={testId}
      role={interactive ? 'button' : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={interactive ? handleActivate : undefined}
      onKeyDown={interactive ? handleActivate : undefined}
      className={cn(
        'flex w-full flex-col gap-2 rounded-lg border border-border bg-surface-raised px-3 py-3 text-left',
        'min-h-12 transition-transform duration-150 ease-out',
        interactive && 'cursor-pointer active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50',
        className,
      )}
    >
      <div className="flex min-w-0 items-start justify-between gap-3">
        <div className="min-w-0 flex-1 overflow-hidden">
          <p className="truncate font-mono text-sm font-bold text-text">{identity}</p>
          <p className="mt-0.5 truncate text-sm text-text-muted" title={title}>
            {title}
          </p>
        </div>
        {status ? <div className="shrink-0">{status}</div> : null}
      </div>
      {(amount || date) && (
        <div className="flex min-w-0 items-center justify-between gap-2 text-sm">
          {amount ? <span className="tabular-nums font-semibold text-text">{amount}</span> : <span />}
          {date ? <span className="text-xs text-text-muted">{date}</span> : null}
        </div>
      )}
      {footer}
    </div>
  );
}
