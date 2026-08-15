import { forwardRef, useImperativeHandle, useRef, type ElementType, type ReactNode } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useScrollFoldHints } from '@/hooks/useScrollFoldHints';

export type ScrollFadePortProps = {
  children: ReactNode;
  className?: string;
  /** Classes on the outer relative shell (chevron host). */
  shellClassName?: string;
  /** Remeasure when these change (e.g. active settings tab). */
  measureKey?: string | number;
  as?: ElementType;
  'aria-label'?: string;
  'data-testid'?: string;
  'data-settings-scrollport'?: string;
  'data-list-scrollport'?: string;
};

/**
 * Scrollport with native scrollbar chrome hidden and fold cues
 * (fade mask + chevrons) matching the primary icon rail.
 */
export const ScrollFadePort = forwardRef<HTMLElement, ScrollFadePortProps>(
  function ScrollFadePort(
    {
      children,
      className,
      shellClassName,
      measureKey,
      as: Comp = 'div',
      'aria-label': ariaLabel,
      'data-testid': testId,
      'data-settings-scrollport': settingsScrollport,
      'data-list-scrollport': listScrollport,
    },
    ref,
  ) {
    const scrollRef = useRef<HTMLElement | null>(null);
    useImperativeHandle(ref, () => scrollRef.current as HTMLElement);
    const { canScrollUp, canScrollDown, hasOverflow } = useScrollFoldHints(scrollRef, [
      measureKey,
    ]);

    return (
      <div
        className={cn('relative min-h-0 min-w-0', shellClassName)}
        data-testid={testId ? `${testId}-shell` : undefined}
      >
        {canScrollUp && (
          <div
            className="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center pt-0.5"
            aria-hidden
            data-testid={testId ? `${testId}-scroll-up` : 'scroll-fade-up'}
          >
            <ChevronUp className="h-3.5 w-3.5 text-text-muted/80" />
          </div>
        )}
        {canScrollDown && (
          <div
            className="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex justify-center pb-0.5"
            aria-hidden
            data-testid={testId ? `${testId}-scroll-down` : 'scroll-fade-down'}
          >
            <ChevronDown className="h-3.5 w-3.5 text-text-muted/80" />
          </div>
        )}
        <Comp
          ref={scrollRef}
          aria-label={ariaLabel}
          data-testid={testId}
          data-settings-scrollport={settingsScrollport}
          data-list-scrollport={listScrollport}
          data-scroll-fade={hasOverflow ? 'overflow' : 'fit'}
          className={cn(
            'min-h-0 overscroll-contain scrollbar-none',
            hasOverflow && 'rail-scroll-mask',
            className,
          )}
        >
          {children}
        </Comp>
      </div>
    );
  },
);
