import { describe, expect, it } from 'vitest';
import {
  refetchIntervalWhileAuthenticated,
  retryUnlessUnauthorized,
} from './queryClient';
import { useSessionStore } from '@/stores/session';

describe('queryClient auth-aware polling', () => {
  it('does not retry 401s', () => {
    expect(
      retryUnlessUnauthorized(0, { isAxiosError: true, response: { status: 401 } }),
    ).toBe(false);
    expect(retryUnlessUnauthorized(0, new Error('timeout'))).toBe(true);
  });

  it('stops refetch intervals after an error or signed-out session', () => {
    useSessionStore.setState({ authenticated: true });
    const interval = refetchIntervalWhileAuthenticated(3_000);
    expect(interval({ state: { status: 'success' } })).toBe(3_000);
    expect(interval({ state: { status: 'error' } })).toBe(false);

    useSessionStore.setState({ authenticated: false });
    expect(interval({ state: { status: 'success' } })).toBe(false);
  });
});
