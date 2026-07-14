import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface TableProps {
  children: ReactNode;
  className?: string;
}

export function Table({ children, className }: TableProps) {
  return (
    <div className="w-full overflow-auto rounded-lg border border-border">
      <table className={cn('w-full text-sm', className)}>{children}</table>
    </div>
  );
}

export function TableHeader({ children }: { children: ReactNode }) {
  return (
    <thead className="border-b border-border bg-surface">
      {children}
    </thead>
  );
}

export function TableBody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-border">{children}</tbody>;
}

export function TableRow({
  children,
  className,
  onClick,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
}) {
  return (
    <tr
      className={cn(
        'bg-surface-raised transition-colors hover:bg-surface-overlay',
        onClick && 'cursor-pointer',
        className
      )}
      onClick={onClick}
    >
      {children}
    </tr>
  );
}

export function TableHead({
  children,
  className,
  align = 'left',
}: {
  children: ReactNode;
  className?: string;
  align?: 'left' | 'right' | 'center';
}) {
  const alignClass = {
    left: 'text-left',
    right: 'text-right',
    center: 'text-center',
  };

  return (
    <th
      className={cn(
        'px-4 py-3 font-medium text-text-muted',
        alignClass[align],
        className
      )}
    >
      {children}
    </th>
  );
}

export function TableCell({
  children,
  className,
  align = 'left',
  mono = false,
}: {
  children: ReactNode;
  className?: string;
  align?: 'left' | 'right' | 'center';
  mono?: boolean;
}) {
  const alignClass = {
    left: 'text-left',
    right: 'text-right',
    center: 'text-center',
  };

  return (
    <td
      className={cn(
        'px-4 py-3 text-text',
        alignClass[align],
        mono && 'font-mono tabular-nums',
        className
      )}
    >
      {children}
    </td>
  );
}
