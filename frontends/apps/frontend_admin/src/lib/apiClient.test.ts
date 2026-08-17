import { beforeEach, describe, expect, it } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';
import { apiClient } from './apiClient';
import { retryUnlessUnauthorized } from './queryClient';
import { useAdminSession } from '@/features/auth/adminSession';

describe('retryUnlessUnauthorized', () => {
  it('does not retry 401s', () => {
    expect(
      retryUnlessUnauthorized(0, { isAxiosError: true, response: { status: 401 } }),
    ).toBe(false);
    expect(retryUnlessUnauthorized(0, new Error('timeout'))).toBe(true);
  });
});

describe('apiClient 401 session teardown', () => {
  function rejectedHandler() {
    const handlers = (
      apiClient.interceptors.response as unknown as {
        handlers: Array<{ rejected?: (error: unknown) => Promise<unknown> }>;
      }
    ).handlers;
    return handlers.find((h) => h.rejected)?.rejected;
  }

  beforeEach(() => {
    useAdminSession.setState({ authenticated: true, email: 'admin@invsys.com' });
  });

  it('clears the admin session on a protected 401', async () => {
    const rejected = rejectedHandler();
    await expect(
      rejected?.({
        message: 'Unauthorized',
        isAxiosError: true,
        response: { status: 401, headers: {} },
        config: { url: '/api/v1/control-plane/tenants' } as InternalAxiosRequestConfig,
      }),
    ).rejects.toBeTruthy();
    expect(useAdminSession.getState().authenticated).toBe(false);
  });

  it('does not clear the session on a login 401', async () => {
    const rejected = rejectedHandler();
    await expect(
      rejected?.({
        message: 'Unauthorized',
        isAxiosError: true,
        response: { status: 401, headers: {} },
        config: { url: '/api/v1/control-plane/auth/login' } as InternalAxiosRequestConfig,
      }),
    ).rejects.toBeTruthy();
    expect(useAdminSession.getState().authenticated).toBe(true);
  });
});
