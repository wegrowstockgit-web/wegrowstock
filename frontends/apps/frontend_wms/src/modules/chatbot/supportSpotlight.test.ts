import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { expandSidebarForPath, spotlightSelector } from './supportSpotlight';

describe('supportSpotlight', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('applies support-spotlight-ring for 3 seconds then removes it', () => {
    document.body.innerHTML = '<button data-tour="allocate-order">Allocate</button>';
    const el = document.querySelector('[data-tour="allocate-order"]') as HTMLElement;
    el.scrollIntoView = vi.fn();

    expect(spotlightSelector('[data-tour="allocate-order"]')).toBe(true);
    expect(el.classList.contains('support-spotlight-ring')).toBe(true);

    vi.advanceTimersByTime(2999);
    expect(el.classList.contains('support-spotlight-ring')).toBe(true);
    vi.advanceTimersByTime(2);
    expect(el.classList.contains('support-spotlight-ring')).toBe(false);
  });

  it('returns false when selector matches nothing', () => {
    expect(spotlightSelector('[data-tour="missing"]')).toBe(false);
  });

  it('dispatches expand-nav for sidebar accordion expansion', () => {
    const spy = vi.fn();
    window.addEventListener('invsys:expand-nav', spy);
    expandSidebarForPath('/purchase-orders?tab=open');
    expect(spy).toHaveBeenCalled();
    const detail = (spy.mock.calls[0][0] as CustomEvent).detail as { path: string };
    expect(detail.path).toBe('/purchase-orders');
    window.removeEventListener('invsys:expand-nav', spy);
  });
});
