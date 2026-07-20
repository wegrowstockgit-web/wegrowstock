const SPOTLIGHT_CLASS = 'support-spotlight-ring';
const SPOTLIGHT_MS = 3000;

/**
 * Flashes a highlight ring around the first matching DOM element for ~3 seconds.
 */
export function spotlightSelector(selector: string): boolean {
  if (!selector || typeof document === 'undefined') return false;
  let el: Element | null = null;
  try {
    el = document.querySelector(selector);
  } catch {
    // Invalid selector — try comma-separated candidates individually.
    for (const part of selector.split(',').map((s) => s.trim()).filter(Boolean)) {
      try {
        el = document.querySelector(part);
        if (el) break;
      } catch {
        /* ignore */
      }
    }
  }
  if (!el || !(el instanceof HTMLElement)) return false;

  el.classList.add(SPOTLIGHT_CLASS);
  el.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
  window.setTimeout(() => {
    el?.classList.remove(SPOTLIGHT_CLASS);
  }, SPOTLIGHT_MS);
  return true;
}

/** Ask the sidebar to expand the accordion group that owns this path. */
export function expandSidebarForPath(path: string): void {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(
    new CustomEvent('invsys:expand-nav', {
      detail: { path: path.split('?')[0] || path },
    }),
  );
}
