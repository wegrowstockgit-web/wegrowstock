import { create } from 'zustand';

export type UiActionType =
  | 'CLICK'
  | 'FORM_SUBMIT'
  | 'TOAST_ERROR'
  | 'TAB_CHANGE'
  | 'SCAN_REJECTED';

export type UiActionBreadcrumb = {
  timestamp: number;
  actionType: UiActionType;
  elementLabel: string;
  errorMessage?: string | null;
};

const MAX_BUFFER = 10;

type UiActionTrackerState = {
  actions: UiActionBreadcrumb[];
  trackAction: (entry: Omit<UiActionBreadcrumb, 'timestamp'> & { timestamp?: number }) => void;
  getRecentBreadcrumbs: (limit?: number) => UiActionBreadcrumb[];
  clear: () => void;
};

export const useUiActionTrackerStore = create<UiActionTrackerState>((set, get) => ({
  actions: [],
  trackAction: (entry) => {
    const next: UiActionBreadcrumb = {
      timestamp: entry.timestamp ?? Date.now(),
      actionType: entry.actionType,
      elementLabel: (entry.elementLabel || 'Unknown control').trim().slice(0, 160),
      errorMessage: entry.errorMessage?.trim() ? entry.errorMessage.trim().slice(0, 240) : null,
    };
    set((state) => ({
      actions: [...state.actions, next].slice(-MAX_BUFFER),
    }));
    // Optional chatbot training sandbox (no-op when module stubbed).
    void import('@/lib/chatbot/active')
      .then(({ getTrainingGuard }) => {
        getTrainingGuard().onUiAction(next.elementLabel);
      })
      .catch(() => {
        // module absent / stub — ignore
      });
  },
  getRecentBreadcrumbs: (limit = 5) => get().actions.slice(-Math.max(1, limit)),
  clear: () => set({ actions: [] }),
}));

function labelFromElement(el: Element | null): string {
  if (!el || !(el instanceof HTMLElement)) return 'Unknown control';
  const testId = el.getAttribute('data-testid');
  const aria = el.getAttribute('aria-label');
  const text = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  const name = el.getAttribute('name') || el.getAttribute('placeholder');
  return (aria || text || name || testId || el.tagName).slice(0, 160);
}

let installed = false;

/** Capture clicks, form submits, and toast errors for the support copilot temporal memory. */
export function installUiActionTracker(): void {
  if (installed || typeof document === 'undefined') return;
  installed = true;

  document.addEventListener(
    'click',
    (event) => {
      const target = event.target as Element | null;
      const interactive = target?.closest?.(
        'button, a, [role="button"], input[type="submit"], [data-testid]',
      );
      if (!interactive) return;
      const label = labelFromElement(interactive);
      if (!label || label === 'Unknown control') return;
      useUiActionTrackerStore.getState().trackAction({
        actionType: 'CLICK',
        elementLabel: label,
      });
    },
    true,
  );

  document.addEventListener(
    'submit',
    (event) => {
      const form = event.target as HTMLFormElement | null;
      const label =
        form?.getAttribute('aria-label')
        || form?.getAttribute('name')
        || form?.id
        || 'Form submit';
      useUiActionTrackerStore.getState().trackAction({
        actionType: 'FORM_SUBMIT',
        elementLabel: label,
      });
    },
    true,
  );
}

/** Test helper */
export function resetUiActionTrackerForTests(): void {
  installed = false;
  useUiActionTrackerStore.getState().clear();
}
