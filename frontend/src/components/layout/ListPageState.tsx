import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';

interface ListPageStateProps<T> {
  isLoading: boolean;
  isError: boolean;
  error?: Error | null;
  data?: T[];
  refetch: () => void;
  emptyIcon?: React.ComponentType<{ className?: string }>;
  emptyTitle: string;
  emptyDescription: string;
  emptyAction?: ReactNode;
  children: (items: T[]) => ReactNode;
}

export function ListPageState<T>({
  isLoading,
  isError,
  error,
  data,
  refetch,
  emptyIcon,
  emptyTitle,
  emptyDescription,
  emptyAction,
  children,
}: ListPageStateProps<T>) {
  if (isLoading) {
    return (
      <div className="p-6">
        <TableSkeleton rows={8} cols={5} />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-6">
        <EmptyState
          icon={AlertCircle}
          title="Something went wrong"
          description={error?.message ?? 'Failed to load data. Please try again.'}
          action={
            <Button onClick={() => refetch()}>
              <RefreshCw className="h-4 w-4" />
              Retry
            </Button>
          }
        />
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="p-6">
        <EmptyState
          icon={emptyIcon}
          title={emptyTitle}
          description={emptyDescription}
          action={emptyAction}
        />
      </div>
    );
  }

  return <>{children(data)}</>;
}

export function useListQuery<T>(key: string[], url: string) {
  return useQuery({
    queryKey: key,
    queryFn: async () => {
      const res = await apiClient.get<T[]>(url);
      return res.data;
    },
  });
}
