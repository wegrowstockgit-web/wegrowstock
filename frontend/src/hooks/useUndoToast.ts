import { useCallback, useRef, useState } from 'react';

interface PendingAction {
  id: string;
  message: string;
  execute: () => void | Promise<void>;
  rollback?: () => void;
}

export function useUndoToast(durationMs = 5000) {
  const [visible, setVisible] = useState(false);
  const [message, setMessage] = useState('');
  const pendingRef = useRef<PendingAction | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimer = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const dismiss = useCallback(() => {
    clearTimer();
    const pending = pendingRef.current;
    pendingRef.current = null;
    setVisible(false);
    if (pending) void pending.execute();
  }, []);

  const schedule = useCallback(
    (action: Omit<PendingAction, 'id'>) => {
      clearTimer();
      if (pendingRef.current?.rollback) {
        pendingRef.current.rollback();
      }

      const id = crypto.randomUUID();
      pendingRef.current = { id, ...action };
      setMessage(action.message);
      setVisible(true);

      timerRef.current = setTimeout(() => {
        dismiss();
      }, durationMs);
    },
    [durationMs, dismiss]
  );

  const undo = useCallback(() => {
    clearTimer();
    if (pendingRef.current?.rollback) {
      pendingRef.current.rollback();
    }
    pendingRef.current = null;
    setVisible(false);
  }, []);

  return { visible, message, schedule, undo, dismiss };
}
