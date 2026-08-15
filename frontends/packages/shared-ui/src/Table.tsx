import type { HTMLAttributes, ReactNode, TdHTMLAttributes } from 'react';
import { cn } from './cn';

interface TableProps {
  children: ReactNode;
  className?: string;
}

export function Table({ children, className }: TableProps) {
  return (
    <div className="w-full rounded-lg border border-border" data-table-shell="true">
      <table className={cn('w-full border-separate border-spacing-0 text-sm', className)}>
        {children}
      </table>
    </div>
  );
}

export function TableHeader({ children, className }: { children: ReactNode; className?: string }) {
  return <thead className={cn('bg-surface-raised text-left text-text-muted', className)}>{children}</thead>;
}

export function TableBody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-border">{children}</tbody>;
}

export function TableRow({
  children,
  className,
  onClick,
  ...rest
}: HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      className={cn(
        'hover:bg-surface-overlay/60',
        onClick ? 'cursor-pointer' : undefined,
        className,
      )}
      onClick={onClick}
      {...rest}
    >
      {children}
    </tr>
  );
}

export function TableHead({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <th className={cn('px-3 py-2 font-medium sticky top-0 bg-surface-raised', className)}>
      {children}
    </th>
  );
}

export function TableCell({
  children,
  className,
  ...rest
}: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={cn('px-3 py-2 align-middle', className)} {...rest}>
      {children}
    </td>
  );
}
