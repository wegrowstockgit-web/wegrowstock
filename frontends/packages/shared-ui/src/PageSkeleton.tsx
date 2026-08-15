export function PageSkeleton({ label = 'Loading…' }: { label?: string }) {
  return (
    <div
      className="animate-pulse space-y-4 p-6"
      role="status"
      aria-busy="true"
      data-testid="page-skeleton"
    >
      <div className="h-6 w-48 rounded bg-border/60" />
      <div className="h-4 w-full max-w-xl rounded bg-border/40" />
      <div className="h-4 w-full max-w-lg rounded bg-border/40" />
      <div className="mt-6 h-40 rounded border border-border bg-surface" />
      <span className="sr-only">{label}</span>
    </div>
  );
}
