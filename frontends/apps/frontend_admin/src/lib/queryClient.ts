import { QueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { useAdminSession } from '@/features/auth/adminSession';

export function isUnauthorizedError(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

export function retryUnlessUnauthorized(failureCount: number, error: unknown): boolean {
  if (isUnauthorizedError(error)) return false;
  return failureCount < 1;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: retryUnlessUnauthorized,
      refetchOnWindowFocus: () => useAdminSession.getState().authenticated,
    },
  },
});
