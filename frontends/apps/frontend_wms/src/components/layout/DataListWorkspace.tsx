import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/utils';

export interface DataListWorkspaceProps extends HTMLAttributes<HTMLDivElement> {
  /** Optional toolbar rendered above the constrained scrollport. */
  toolbar?: ReactNode;
  /** Must be a virtualized list (VirtualizedTable) or an equivalent bounded child. */
  children: ReactNode;
  testId?: string;
}

/**
 * Constrained workspace for Surface A data grids.
 * The content pane is `min-h-0 flex-1 overflow-hidden` so VirtualizedTable
 * can take a real viewport instead of growing until the tab OOMs.
 */
export function DataListWorkspace({
  toolbar,
  children,
  testId = 'data-list-workspace',
  className,
  ...rest
}: DataListWorkspaceProps) {
  return (
    <div
      {...rest}
      data-testid={testId}
      className={cn(
        'flex min-h-0 min-w-0 w-full flex-1 flex-col overflow-hidden',
        className,
      )}
    >
      {toolbar ? <div className="shrink-0">{toolbar}</div> : null}
      <div
        className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
        data-testid="data-list-workspace-content"
      >
        {children}
      </div>
    </div>
  );
}
