import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useSessionStore } from '@/stores/session';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useDesktopIdle } from './useDesktopIdle';

describe('useDesktopIdle', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
    usePreferencesStore.getState().setDesktopIdleTimeoutMinutes(30);
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        warehouseIds: [],
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    } as never);
  });

  afterEach(() => {
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('enters warning two minutes before lock then locks', async () => {
    sessionStorage.setItem('invsys.desktopIdleTimeoutMs', '4000');
    const { result } = renderHook(() => useDesktopIdle({ graceMs: 2000 }));

    expect(result.current.isWarningPhase).toBe(false);
    expect(result.current.isLocked).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    expect(result.current.isWarningPhase).toBe(true);
    expect(result.current.isLocked).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    expect(result.current.isLocked).toBe(true);
    expect(result.current.isWarningPhase).toBe(false);
  });

  it('resets on activity and staySignedIn', async () => {
    sessionStorage.setItem('invsys.desktopIdleTimeoutMs', '4000');
    const { result } = renderHook(() => useDesktopIdle({ graceMs: 2000 }));

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    expect(result.current.isWarningPhase).toBe(true);

    await act(async () => {
      window.dispatchEvent(new Event('mousemove'));
    });
    expect(result.current.isWarningPhase).toBe(false);
    expect(result.current.isLocked).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(1999);
    });
    expect(result.current.isLocked).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current.isWarningPhase).toBe(true);

    await act(async () => {
      result.current.staySignedIn();
    });
    expect(result.current.isWarningPhase).toBe(false);

    await act(async () => {
      vi.advanceTimersByTime(1999);
    });
    expect(result.current.isLocked).toBe(false);
  });

  it('does not arm when disabled or signed out', async () => {
    sessionStorage.setItem('invsys.desktopIdleTimeoutMs', '1000');
    const { result, rerender } = renderHook(
      ({ enabled }: { enabled: boolean }) => useDesktopIdle({ enabled, graceMs: 500 }),
      { initialProps: { enabled: false } },
    );

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });
    expect(result.current.isLocked).toBe(false);

    useSessionStore.setState({ authenticated: false } as never);
    await act(async () => {
      rerender({ enabled: true });
      vi.advanceTimersByTime(2000);
    });
    expect(result.current.isLocked).toBe(false);
  });

  it('lockNow and unlock restore an idle session', async () => {
    sessionStorage.setItem('invsys.desktopIdleTimeoutMs', '10000');
    const { result } = renderHook(() => useDesktopIdle({ graceMs: 2000 }));

    await act(async () => {
      (
        window as Window & { __INVSYS_DESKTOP_IDLE__?: { lockNow: () => void } }
      ).__INVSYS_DESKTOP_IDLE__?.lockNow();
    });
    expect(result.current.isLocked).toBe(true);

    await act(async () => {
      result.current.unlock();
    });
    expect(result.current.isLocked).toBe(false);
  });
});
