import { cn } from '@/lib/utils';

export function LiveConnectionBadge({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-white bg-[#1d70cb]',
        className,
      )}
      data-testid="live-connection-badge"
    >
      LIVE
    </span>
  );
}
