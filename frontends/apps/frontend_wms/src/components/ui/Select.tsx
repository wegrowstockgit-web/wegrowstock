import { forwardRef, useId, type SelectHTMLAttributes } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  tone?: 'default' | 'inverse';
}

function slugify(label: string): string {
  return label.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-_]/g, '');
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, id, name, tone = 'default', children, ...props }, ref) => {
    const reactId = useId();
    const selectId = id ?? (label ? `${reactId}-${slugify(label)}` : reactId);
    const selectName = name ?? (id ? id : label ? slugify(label) : undefined);

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={selectId}
            className={cn('text-sm font-medium', tone === 'inverse' ? 'text-white/80' : 'text-text')}
          >
            {label}
          </label>
        )}
        <div className="relative">
          <select
            ref={ref}
            {...props}
            id={selectId}
            name={selectName}
            className={cn(
              'h-10 w-full appearance-none rounded-md border px-3 pr-9 text-sm',
              tone === 'inverse'
                ? 'border-white/15 bg-[#1a2332] text-white focus:border-[#55ACEE] focus:outline-none focus:ring-2 focus:ring-[#55ACEE]/30'
                : 'border-border bg-surface-raised text-text focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20',
              error && 'border-danger focus:border-danger focus:ring-danger/20',
              className,
            )}
          >
            {children}
          </select>
          <ChevronDown
            className={cn(
              'pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2',
              tone === 'inverse' ? 'text-white/50' : 'text-text-muted',
            )}
          />
        </div>
        {error && <p className="text-xs text-danger">{error}</p>}
      </div>
    );
  },
);

Select.displayName = 'Select';
