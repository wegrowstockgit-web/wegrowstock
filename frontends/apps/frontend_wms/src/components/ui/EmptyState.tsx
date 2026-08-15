import type { ComponentType, ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface EmptyStateProps {
  icon?: ComponentType<{ className?: string }>;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-lg border border-dashed border-border',
        'bg-surface-raised px-6 py-16 text-center',
        className
      )}
    >
      {Icon && (
        <div className="mb-4 rounded-full bg-accent-muted p-4">
          <Icon className="h-8 w-8 text-accent" />
        </div>
      )}
      <h3 className="text-lg font-semibold text-text">{title}</h3>
      {description && (
        <p className="mt-2 max-w-sm text-sm text-text-muted">{description}</p>
      )}
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
