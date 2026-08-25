import { useEffect, useState } from 'react';
import { Unlink } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

/**
 * Pack-station weight: scale fills the field when stable; operators can unlink
 * for a typed override if the scale drops or the reading is wrong.
 */
export function PackWeightCapture({
  scaleConnected,
  stableWeightLb,
  liveWeightLb,
  value,
  onChange,
  placeholder = 'Optional',
}: {
  scaleConnected: boolean;
  stableWeightLb: number | null;
  liveWeightLb?: number | null;
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
}) {
  const [manualOverride, setManualOverride] = useState(false);
  const locked = scaleConnected && !manualOverride;
  const autoLb =
    stableWeightLb != null && stableWeightLb > 0
      ? stableWeightLb
      : liveWeightLb != null && liveWeightLb > 0
        ? liveWeightLb
        : null;

  useEffect(() => {
    if (!locked || autoLb == null) return;
    onChange(autoLb.toFixed(2));
  }, [locked, autoLb, onChange]);

  return (
    <div className="flex items-end gap-2" data-testid="pack-weight-capture">
      <div className="min-w-0 flex-1">
        <Input
          type="number"
          min="0"
          step="0.01"
          label="Weight override (lb)"
          data-testid="pack-weight-input"
          readOnly={locked}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
        />
      </div>
      <Button
        type="button"
        variant={manualOverride ? 'primary' : 'secondary'}
        size="sm"
        className={cn('min-h-11 min-w-11 shrink-0', manualOverride && 'ring-2 ring-accent/40')}
        aria-pressed={manualOverride}
        aria-label={manualOverride ? 'Use scale weight' : 'Manual weight override'}
        data-testid="pack-weight-manual-override"
        title={
          scaleConnected
            ? manualOverride
              ? 'Return to scale weight'
              : 'Type weight manually'
            : 'Scale disconnected — typing is enabled'
        }
        onClick={() => setManualOverride((open) => !open)}
      >
        <Unlink className="h-4 w-4" aria-hidden />
      </Button>
    </div>
  );
}
