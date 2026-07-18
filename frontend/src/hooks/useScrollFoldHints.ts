import { useCallback, useEffect, useState, type RefObject } from 'react';

export type ScrollFoldHints = {
  canScrollUp: boolean;
  canScrollDown: boolean;
  hasOverflow: boolean;
};

/**
 * Tracks whether a scrollport has more content above/below the fold.
 * Used with scrollbar-none + fade mask (same pattern as the icon rail).
 */
export function useScrollFoldHints(
  ref: RefObject<HTMLElement | null>,
  deps: unknown[] = [],
): ScrollFoldHints {
  const [canScrollUp, setCanScrollUp] = useState(false);
  const [canScrollDown, setCanScrollDown] = useState(false);

  const update = useCallback(() => {
    const el = ref.current;
    if (!el) {
      setCanScrollUp(false);
      setCanScrollDown(false);
      return;
    }
    const { scrollTop, scrollHeight, clientHeight } = el;
    const overflow = scrollHeight > clientHeight + 1;
    setCanScrollUp(overflow && scrollTop > 4);
    setCanScrollDown(overflow && scrollTop + clientHeight < scrollHeight - 4);
  }, [ref]);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    update();
    el.addEventListener('scroll', update, { passive: true });
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(update) : null;
    ro?.observe(el);
    // Re-measure when children mutate (tab switches, async content).
    const mo =
      typeof MutationObserver !== 'undefined'
        ? new MutationObserver(update)
        : null;
    mo?.observe(el, { childList: true, subtree: true, characterData: true });
    return () => {
      el.removeEventListener('scroll', update);
      ro?.disconnect();
      mo?.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- caller passes explicit rebind deps
  }, [update, ref, ...deps]);

  return {
    canScrollUp,
    canScrollDown,
    hasOverflow: canScrollUp || canScrollDown,
  };
}
