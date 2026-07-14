import { type ReactNode } from 'react';
import { SlideOutDrawer, type SlideOutDrawerProps } from '@/components/ui/SlideOutDrawer';

export type RightPeekDrawerProps = SlideOutDrawerProps;

/**
 * Office Surface A peek drawer — slides over the active grid without losing
 * filter state or scroll position. Thin alias over SlideOutDrawer for the
 * enterprise drawer architecture.
 */
export function RightPeekDrawer({ children, ...props }: RightPeekDrawerProps & { children: ReactNode }) {
  return <SlideOutDrawer {...props}>{children}</SlideOutDrawer>;
}
