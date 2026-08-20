import { describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { useFeatureFlag } from './useFeatureFlag';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return createElement(QueryClientProvider, { client }, children);
}

describe('useFeatureFlag', () => {
  it('loads the tenant flag list and reports isEnabled', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { flags: ['beta-dock', 'global-wave'] } });
    const { result } = renderHook(() => useFeatureFlag('beta-dock'), { wrapper });
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    expect(result.current.flags).toEqual(['beta-dock', 'global-wave']);
    expect(result.current.isEnabled).toBe(true);
    expect(result.current.hasFlag('global-wave')).toBe(true);
    expect(result.current.hasFlag('missing')).toBe(false);
  });
});
