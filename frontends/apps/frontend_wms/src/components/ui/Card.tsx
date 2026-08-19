import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface CardProps {
  children: ReactNode;
  className?: string;
  padding?: 'none' | 'sm' | 'md' | 'lg';
  'data-testid'?: string;
}

export function Card({ children, className, padding = 'md', 'data-testid': testId }: CardProps) {
  const paddingClasses = {
    none: '',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
  };

  return (
    <div
      data-testid={testId}
      className={cn(
        'rounded-lg border border-border bg-surface-raised shadow-card',
        paddingClasses[padding],
        className
      )}
    >
      {children}
    </div>
  );
}

interface CardHeaderProps {
  title: string;
  description?: string;
  action?: ReactNode;
}

export function CardHeader({ title, description, action }: CardHeaderProps) {
  return (
    <div className="mb-4">
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <h3 className="min-w-0 text-lg font-semibold text-text">{title}</h3>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      {description && (
        <p className="mt-1 max-w-prose text-sm text-text-muted">{description}</p>
      )}
    </div>
  );
}
