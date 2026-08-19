import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SessionHydrationGate } from './SessionHydrationGate';
import { useSessionStore } from '@/stores/session';
import { apiClient, endSessionOnAuthFailure } from '@/api/client';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return {
    ...actual,
    apiClient: {
      get: vi.fn(),
    },
    endSessionOnAuthFailure: vi.fn(),
  };
});

describe('SessionHydrationGate', () => {
  beforeEach(() => {
    vi.mocked(endSessionOnAuthFailure).mockReset();
    vi.mocked(apiClient.get).mockReset();
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        warehouseIds: [],
        avatarUrl: null,
        tenantId: 't1',
      },
    });
    vi.stubGlobal(
      'EventSource',
      class {
        addEventListener() {}
        close() {}
        onerror: (() => void) | null = null;
      },
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('ends the session instead of polling when /auth/me returns 401', async () => {
    vi.mocked(apiClient.get).mockRejectedValue({
      isAxiosError: true,
      response: { status: 401 },
      message: 'Unauthorized',
    });

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <SessionHydrationGate>
          <div>app</div>
        </SessionHydrationGate>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(endSessionOnAuthFailure).toHaveBeenCalled();
    });
  });

  it('rewrites non-login routes that carry ?handoff= onto /login', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', {
      search: '?handoff=abc',
      pathname: '/dashboard',
      replace,
    });
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        userId: 'u1',
        tenantId: 't1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
      },
    });

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <SessionHydrationGate>
          <div>app</div>
        </SessionHydrationGate>
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(replace).toHaveBeenCalledWith('/login?handoff=abc');
    });
  });
});
