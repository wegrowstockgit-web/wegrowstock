import { useCallback, useEffect, useRef, useState } from 'react';
import { apiClient } from '@/api/client';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useIsAuthenticated, useSessionStore } from '@/stores/session';

export const DESKTOP_IDLE_TIMEOUT_OPTIONS = [
  { value: 15, label: '15 minutes' },
  { value: 30, label: '30 minutes' },
  { value: 60, label: '1 hour' },
  { value: 240, label: '4 hours' },
] as const;

export const DEFAULT_DESKTOP_IDLE_TIMEOUT_MINUTES = 30;
export const DESKTOP_IDLE_GRACE_MS = 2 * 60 * 1000;

const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'scroll'] as const;

export type UseDesktopIdleOptions = {
  timeoutMinutes?: number;
  graceMs?: number;
  enabled?: boolean;
};

export type DesktopIdleState = {
  isWarningPhase: boolean;
  isLocked: boolean;
  staySignedIn: () => void;
  unlock: () => void;
};

function resolveTimeoutMs(timeoutMinutes: number): number {
  const minutes =
    timeoutMinutes === 15 || timeoutMinutes === 30 || timeoutMinutes === 60 || timeoutMinutes === 240
      ? timeoutMinutes
      : DEFAULT_DESKTOP_IDLE_TIMEOUT_MINUTES;
  const overrideMs = readE2eOverrideMs();
  return overrideMs ?? minutes * 60_000;
}

let desktopLockInFlight: Promise<void> = Promise.resolve();

/** Wait for the in-flight POST /auth/lock so unlock cannot lose a race. */
export function waitForDesktopLockRequest(): Promise<void> {
  return desktopLockInFlight;
}

function readE2eOverrideMs(): number | null {
  if (typeof window === 'undefined') return null;
  const raw = window.sessionStorage.getItem('invsys.desktopIdleTimeoutMs');
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

/**
 * Office idle detector — 2-minute grace warning, then a biometric-first soft-lock.
 * Floor scanner PIN lock is a separate path (`useScannerIdle`).
 */
export function useDesktopIdle(options: UseDesktopIdleOptions = {}): DesktopIdleState {
  const storeMinutes = usePreferencesStore((s) => s.desktopIdleTimeoutMinutes);
  const authenticated = useIsAuthenticated();
  const timeoutMinutes = options.timeoutMinutes ?? storeMinutes;
  const graceMs = options.graceMs ?? DESKTOP_IDLE_GRACE_MS;
  const enabled = options.enabled ?? true;
  const [isWarningPhase, setWarningPhase] = useState(false);
  const isLocked = useSessionStore((s) => s.isLocked);
  const setLocked = useSessionStore((s) => s.setLocked);
  const [epoch, setEpoch] = useState(0);
  const warningTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const lockTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const lockedRef = useRef(false);

  const flagLocked = useCallback(() => {
    lockedRef.current = true;
    setWarningPhase(false);
    setLocked(true);
    desktopLockInFlight = apiClient
      .post('/api/v1/auth/lock')
      .then(() => undefined)
      .catch(() => undefined);
  }, [setLocked]);

  const clearTimers = useCallback(() => {
    if (warningTimer.current) clearTimeout(warningTimer.current);
    if (lockTimer.current) clearTimeout(lockTimer.current);
    warningTimer.current = undefined;
    lockTimer.current = undefined;
  }, []);

  const staySignedIn = useCallback(() => {
    setWarningPhase(false);
    setLocked(false);
    lockedRef.current = false;
    setEpoch((value) => value + 1);
  }, [setLocked]);

  const unlock = useCallback(() => {
    setLocked(false);
    setWarningPhase(false);
    lockedRef.current = false;
  }, [setLocked]);

  useEffect(() => {
    lockedRef.current = isLocked;
  }, [isLocked]);

  useEffect(() => {
    const armed = enabled && authenticated && !isLocked;
    const timeoutMs = resolveTimeoutMs(timeoutMinutes);
    const warnAfter = Math.max(0, timeoutMs - graceMs);

    const arm = () => {
      if (lockedRef.current) return;
      clearTimers();
      setWarningPhase(false);
      warningTimer.current = setTimeout(() => {
        setWarningPhase(true);
      }, warnAfter);
      lockTimer.current = setTimeout(() => {
        flagLocked();
      }, timeoutMs);
    };

    const api = {
      lockNow: () => {
        clearTimers();
        flagLocked();
      },
      staySignedIn,
    };
    (
      window as Window & {
        __INVSYS_DESKTOP_IDLE__?: typeof api;
      }
    ).__INVSYS_DESKTOP_IDLE__ = api;

    if (!armed) {
      clearTimers();
      return;
    }

    arm();
    for (const event of ACTIVITY_EVENTS) {
      window.addEventListener(event, arm, { passive: true, capture: true });
    }

    return () => {
      clearTimers();
      for (const event of ACTIVITY_EVENTS) {
        window.removeEventListener(event, arm, { capture: true });
      }
    };
  }, [enabled, authenticated, isLocked, timeoutMinutes, graceMs, clearTimers, epoch, flagLocked, staySignedIn]);

  return { isWarningPhase, isLocked, staySignedIn, unlock };
}
