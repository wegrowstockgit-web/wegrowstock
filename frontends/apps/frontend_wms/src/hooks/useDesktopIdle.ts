import { useCallback, useEffect, useRef, useState } from 'react';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useIsAuthenticated } from '@/stores/session';

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
  const [isLocked, setLocked] = useState(false);
  const [epoch, setEpoch] = useState(0);
  const warningTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const lockTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const lockedRef = useRef(false);

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
  }, []);

  const unlock = useCallback(() => {
    setLocked(false);
    setWarningPhase(false);
    lockedRef.current = false;
  }, []);

  useEffect(() => {
    lockedRef.current = isLocked;
  }, [isLocked]);

  useEffect(() => {
    const armed = enabled && authenticated && !isLocked;
    if (!armed) {
      clearTimers();
      return;
    }

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
        lockedRef.current = true;
        setWarningPhase(false);
        setLocked(true);
      }, timeoutMs);
    };

    arm();
    for (const event of ACTIVITY_EVENTS) {
      window.addEventListener(event, arm, { passive: true, capture: true });
    }

    const api = {
      lockNow: () => {
        clearTimers();
        lockedRef.current = true;
        setWarningPhase(false);
        setLocked(true);
      },
      staySignedIn: () => {
        staySignedIn();
        arm();
      },
    };
    (
      window as Window & {
        __INVSYS_DESKTOP_IDLE__?: typeof api;
      }
    ).__INVSYS_DESKTOP_IDLE__ = api;

    return () => {
      clearTimers();
      for (const event of ACTIVITY_EVENTS) {
        window.removeEventListener(event, arm, { capture: true });
      }
    };
  }, [enabled, authenticated, isLocked, timeoutMinutes, graceMs, clearTimers, epoch]);

  return { isWarningPhase, isLocked, staySignedIn, unlock };
}
