import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface BigButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: 'primary' | 'secondary' | 'success' | 'danger';
  icon?: ReactNode;
  loading?: boolean;
}

export function BigButton({
  children,
  variant = 'primary',
  icon,
  loading = false,
  className,
  disabled,
  ...props
}: BigButtonProps) {
  const variants = {
    primary: 'bg-accent text-text-inverse active:bg-accent-hover',
    secondary: 'bg-surface-raised text-text border border-border active:bg-surface-overlay',
    success: 'bg-success text-white active:opacity-90',
    danger: 'bg-danger text-white active:opacity-90',
  };

  return (
    <button
      className={cn(
        'flex min-h-tap w-full items-center justify-center gap-3 rounded-xl px-6 py-4',
        'text-lg font-semibold shadow-elevated transition-transform active:scale-[0.98]',
        'disabled:opacity-50 disabled:pointer-events-none',
        variants[variant],
        className
      )}
      disabled={disabled || loading}
      {...props}
    >
      {icon}
      {loading ? 'Working…' : children}
    </button>
  );
}
