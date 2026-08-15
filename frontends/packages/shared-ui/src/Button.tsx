import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from './cn';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = 'primary',
      size = 'md',
      loading = false,
      disabled,
      children,
      ...props
    },
    ref
  ) => {
    const variants = {
      primary:
        'bg-accent text-text-inverse hover:bg-accent-hover shadow-sm',
      secondary:
        'bg-surface-raised text-text border border-border hover:bg-surface-overlay',
      ghost: 'text-text-muted hover:text-text hover:bg-surface-overlay',
      danger: 'bg-danger text-white hover:opacity-90',
    };

    const sizes = {
      sm: 'h-8 px-3 text-sm rounded-sm',
      md: 'h-10 px-4 text-sm rounded-md',
      lg: 'h-12 px-6 text-base rounded-lg',
    };

    return (
      <button
        ref={ref}
        className={cn(
          'inline-flex items-center justify-center gap-2 font-medium transition-colors',
          'disabled:opacity-50 disabled:pointer-events-none',
          variants[variant],
          sizes[size],
          className
        )}
        disabled={disabled || loading}
        {...props}
      >
        {loading && (
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
        )}
        {children}
      </button>
    );
  }
);

Button.displayName = 'Button';
