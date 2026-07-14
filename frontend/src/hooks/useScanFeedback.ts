import { useCallback, useEffect, useRef, useState } from 'react';

export type ScanFeedbackType = 'success' | 'error' | null;

interface ScanFeedbackState {
  flash: ScanFeedbackType;
  triggerSuccess: () => void;
  triggerError: () => void;
}

let audioContext: AudioContext | null = null;

function ensureAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  if (!audioContext) {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (Ctx) audioContext = new Ctx();
  }
  return audioContext;
}

function playTone(frequency: number, durationMs: number): void {
  const ctx = ensureAudioContext();
  if (!ctx) return;

  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();
  oscillator.type = 'sine';
  oscillator.frequency.value = frequency;
  gain.gain.value = 0.15;
  oscillator.connect(gain);
  gain.connect(ctx.destination);
  oscillator.start();
  oscillator.stop(ctx.currentTime + durationMs / 1000);
}

function vibrate(pattern: number | number[]): void {
  if ('vibrate' in navigator) {
    navigator.vibrate(pattern);
  }
}

export function useScanFeedback(): ScanFeedbackState {
  const [flash, setFlash] = useState<ScanFeedbackType>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const clearFlash = useCallback(() => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    timeoutRef.current = setTimeout(() => setFlash(null), 250);
  }, []);

  const unlockAudio = useCallback(() => {
    const ctx = ensureAudioContext();
    if (ctx?.state === 'suspended') {
      void ctx.resume();
    }
  }, []);

  useEffect(() => {
    const unlock = () => unlockAudio();
    window.addEventListener('pointerdown', unlock, { once: true });
    window.addEventListener('keydown', unlock, { once: true });
    return () => {
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
  }, [unlockAudio]);

  const triggerSuccess = useCallback(() => {
    unlockAudio();
    vibrate(50);
    playTone(880, 80);
    setFlash('success');
    clearFlash();
  }, [unlockAudio, clearFlash]);

  const triggerError = useCallback(() => {
    unlockAudio();
    vibrate([100, 50, 100]);
    playTone(220, 120);
    setFlash('error');
    clearFlash();
  }, [unlockAudio, clearFlash]);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  return { flash, triggerSuccess, triggerError };
}
