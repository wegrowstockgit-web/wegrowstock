import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useCurrentNetwork } from './useCurrentNetwork';
import { fetchCurrentNetworkInfo } from '@/api/settings';

vi.mock('@/api/settings', () => ({
  fetchCurrentNetworkInfo: vi.fn(),
}));

function wrap({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('useCurrentNetwork', () => {
  it('fetches network info on mount and exposes refresh', async () => {
    vi.mocked(fetchCurrentNetworkInfo).mockResolvedValue({
      clientIp: '10.0.0.9',
      suggestedCidr: '10.0.0.9/32',
      isPrivateNetwork: true,
      networkHint: 'Internal VPN / LAN',
    });

    const { result } = renderHook(() => useCurrentNetwork(), { wrapper: wrap });
    await waitFor(() => expect(result.current.networkInfo?.clientIp).toBe('10.0.0.9'));
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
    await result.current.refresh();
    expect(fetchCurrentNetworkInfo).toHaveBeenCalledTimes(2);
  });
});
