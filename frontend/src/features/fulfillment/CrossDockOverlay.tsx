import { ArrowRight, PackageCheck, X } from 'lucide-react';
import { BigButton } from '@/components/ui/BigButton';
import { Card } from '@/components/ui/Card';

export interface CrossDockPrompt {
  sku: string;
  stagingPath: string;
  stagingLocationId?: string;
  salesOrderNumber?: string;
  instruction: string;
}

interface CrossDockOverlayProps {
  prompt: CrossDockPrompt;
  onDismiss: () => void;
  awaitingStagingScan?: boolean;
}

/**
 * Surface B — cross-dock intercept card. Replaces standard put-away bin instructions
 * when inbound stock is needed for an open / backordered sales order.
 */
export function CrossDockOverlay({ prompt, onDismiss, awaitingStagingScan }: CrossDockOverlayProps) {
  return (
    <Card
      className="mb-4 border-2 border-accent bg-accent-muted/40"
      padding="md"
      data-testid="cross-dock-overlay"
    >
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <PackageCheck className="h-6 w-6 text-accent" />
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-accent">Cross-Dock</p>
            <p className="text-lg font-bold text-text">Bypass storage put-away</p>
          </div>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="rounded-md p-1 text-text-muted hover:bg-surface-overlay"
          aria-label="Dismiss cross-dock prompt"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <p className="mb-3 text-sm text-text" data-testid="cross-dock-instruction">
        {prompt.instruction}
      </p>

      <div className="mb-3 rounded-lg bg-surface-raised p-3">
        <p className="text-xs font-medium uppercase text-text-muted">Do not put away to</p>
        <p className="font-mono text-sm text-danger line-through" data-testid="cross-dock-bypass-bin">
          Z-A/A-1/B-01
        </p>
        <div className="mt-2 flex items-center gap-2">
          <ArrowRight className="h-4 w-4 text-accent" />
          <div>
            <p className="text-xs font-medium uppercase text-text-muted">Ship staging instead</p>
            <p className="font-mono text-lg font-bold text-accent" data-testid="cross-dock-staging-path">
              {prompt.stagingPath}
            </p>
          </div>
        </div>
        {prompt.salesOrderNumber && (
          <p className="mt-2 text-sm text-text-muted">
            For sales order <span className="font-mono text-text">{prompt.salesOrderNumber}</span>
            {prompt.sku ? (
              <>
                {' '}
                · SKU <span className="font-mono text-text">{prompt.sku}</span>
              </>
            ) : null}
          </p>
        )}
      </div>

      <p className="mb-3 text-sm font-medium text-text">
        {awaitingStagingScan
          ? `Scan location barcode ${prompt.stagingPath} to confirm drop-off`
          : 'Scan the Shipping Staging Zone barcode to confirm'}
      </p>
      <BigButton variant="success" className="w-full" disabled>
        Awaiting staging scan…
      </BigButton>
    </Card>
  );
}
