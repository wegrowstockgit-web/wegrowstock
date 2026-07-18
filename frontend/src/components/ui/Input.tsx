import { forwardRef, useId, type InputHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  tone?: 'default' | 'inverse';
}

function slugify(label: string): string {
  return label.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-_]/g, '');
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, label, error, hint, id, name, tone = 'default', ...props }, ref) => {
    const reactId = useId();
    // Always unique within the document — label alone collides across billing/shipping blocks.
    const inputId = id ?? (label ? `${reactId}-${slugify(label)}` : reactId);
    const inputName = name ?? (id ? id : label ? slugify(label) : undefined);

    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className={cn(
              'text-sm font-medium',
              tone === 'inverse' ? 'text-white/80' : 'text-text',
            )}
          >
            {label}
          </label>
        )}
        <input
          ref={ref}
          {...props}
          id={inputId}
          name={inputName}
          className={cn(
            'h-10 w-full rounded-md border border-border bg-surface-raised px-3 text-sm text-text',
            'placeholder:text-text-muted',
            'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20',
            error && 'border-danger focus:border-danger focus:ring-danger/20',
            className,
          )}
        />
        {error && <p className="text-xs text-danger">{error}</p>}
        {hint && !error && <p className="text-xs text-text-muted">{hint}</p>}
      </div>
    );
  },
);

Input.displayName = 'Input';
