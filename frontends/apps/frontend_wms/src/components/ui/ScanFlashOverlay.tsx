import type { ScanFeedbackType } from '@/hooks/useScanFeedback';
import { cn } from '@/lib/utils';

interface ScanFlashOverlayProps {
  flash: ScanFeedbackType;
}

export function ScanFlashOverlay({ flash }: ScanFlashOverlayProps) {
  if (!flash) return null;

  return (
    <div
      className={cn(
        'pointer-events-none fixed inset-0 z-[9999]',
        flash === 'success' && 'animate-flash-success',
        flash === 'error' && 'animate-flash-error',
        flash === 'pending' && 'animate-flash-pending',
      )}
      data-testid="scan-flash-overlay"
      data-flash={flash}
      aria-hidden
    />
  );
}
