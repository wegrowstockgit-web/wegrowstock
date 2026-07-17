import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';

const STREAM_URL = '/api/v1/dashboard/stream';

type DashboardStreamEvent = {
  eventType?: string;
  payload?: Record<string, unknown>;
  at?: string;
};

/**
 * Subscribes to dashboard SSE and invalidates React Query caches on domain events.
 * Replaces aggressive refetchInterval polling on office surfaces.
 */
export function useDashboardStream(enabled = true): void {
  const queryClient = useQueryClient();
  const queryClientRef = useRef(queryClient);
  queryClientRef.current = queryClient;

  useEffect(() => {
    if (!enabled || typeof EventSource === 'undefined') {
      return;
    }

    let closed = false;
    let source: EventSource | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;

    const invalidateForEvent = (eventType: string) => {
      const qc = queryClientRef.current;
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      void qc.invalidateQueries({ queryKey: ['cycle-counts'] });
      void qc.invalidateQueries({ queryKey: ['office', 'exceptions'] });
      void qc.invalidateQueries({ queryKey: ['forecasting'] });

      if (eventType.includes('STOCK') || eventType.includes('LEDGER') || eventType === 'DASHBOARD_KPI_REFRESHED') {
        void qc.invalidateQueries({ queryKey: ['inventory'] });
      }
      if (eventType.includes('ORDER') || eventType.includes('ALLOCATED')) {
        void qc.invalidateQueries({ queryKey: ['sales-orders'] });
      }
      if (eventType.includes('INVOICE')) {
        void qc.invalidateQueries({ queryKey: ['invoices'] });
      }
      if (eventType.includes('CYCLE')) {
        void qc.invalidateQueries({ queryKey: ['cycle-counts'] });
      }
    };

    const connect = () => {
      if (closed) return;
      source = new EventSource(STREAM_URL, { withCredentials: true });

      source.addEventListener('dashboard', (evt) => {
        attempt = 0;
        try {
          const data = JSON.parse((evt as MessageEvent).data) as DashboardStreamEvent;
          invalidateForEvent(data.eventType ?? 'dashboard');
        } catch {
          invalidateForEvent('dashboard');
        }
      });

      source.addEventListener('connected', () => {
        attempt = 0;
      });

      source.onerror = () => {
        source?.close();
        source = null;
        if (closed) return;
        const delay = Math.min(30_000, 1_000 * 2 ** Math.min(attempt, 5));
        attempt += 1;
        retryTimer = setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      closed = true;
      if (retryTimer) clearTimeout(retryTimer);
      source?.close();
    };
  }, [enabled]);
}
