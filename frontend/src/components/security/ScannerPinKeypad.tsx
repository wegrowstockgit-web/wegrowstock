import { Delete } from 'lucide-react';
import { cn } from '@/lib/utils';

const DIGITS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'back'] as const;

interface ScannerPinKeypadProps {
  value: string;
  maxLength?: number;
  error?: boolean;
  disabled?: boolean;
  onChange: (next: string) => void;
  title: string;
  subtitle?: string;
  /** Optional step cue (e.g. "Step 1 of 2") for multi-step PIN enrollment. */
  stepLabel?: string;
  testIdPrefix?: string;
}

/**
 * Thick-finger / glove-optimized numeric PIN pad for warehouse scanners.
 */
export function ScannerPinKeypad({
  value,
  maxLength = 4,
  error = false,
  disabled = false,
  onChange,
  title,
  subtitle,
  stepLabel,
  testIdPrefix = 'scanner-pin',
}: ScannerPinKeypadProps) {
  function press(digit: string) {
    if (disabled) return;
    if (value.length >= maxLength) return;
    onChange(value + digit);
  }

  function backspace() {
    if (disabled) return;
    onChange(value.slice(0, -1));
  }

  return (
    <div
      className="flex w-full max-w-sm flex-col items-center gap-6 px-4"
      data-testid={`${testIdPrefix}-keypad`}
      data-theme="warehouse"
    >
      <div className="text-center">
        {stepLabel ? (
          <p
            className="mb-2 text-xs font-semibold uppercase tracking-wide text-accent"
            data-testid={`${testIdPrefix}-step`}
          >
            {stepLabel}
          </p>
        ) : null}
        <h1 className="text-2xl font-bold tracking-tight text-text">{title}</h1>
        {subtitle ? <p className="mt-2 text-sm text-text-muted">{subtitle}</p> : null}
      </div>

      <div
        className="flex items-center justify-center gap-4"
        data-testid={`${testIdPrefix}-dots`}
        aria-label={`${value.length} of ${maxLength} digits entered`}
      >
        {Array.from({ length: maxLength }, (_, i) => (
          <span
            key={i}
            className={cn(
              'h-4 w-4 rounded-full border-2 transition-colors',
              i < value.length
                ? error
                  ? 'border-danger bg-danger'
                  : 'border-accent bg-accent'
                : error
                  ? 'border-danger bg-transparent'
                  : 'border-border bg-transparent',
            )}
            data-filled={i < value.length ? 'true' : 'false'}
          />
        ))}
      </div>

      <div className="grid w-full grid-cols-3 gap-3">
        {DIGITS.map((key, index) => {
          if (key === '') {
            return <div key={`spacer-${index}`} />;
          }
          if (key === 'back') {
            return (
              <button
                key="back"
                type="button"
                disabled={disabled}
                onClick={backspace}
                className={cn(
                  'inline-flex min-h-16 min-w-16 items-center justify-center rounded-xl',
                  'bg-surface-raised text-2xl font-bold text-text',
                  'active:scale-[0.97] active:bg-accent active:text-white',
                  'disabled:opacity-50',
                )}
                data-testid={`${testIdPrefix}-back`}
                aria-label="Delete last digit"
              >
                <Delete className="h-7 w-7" aria-hidden />
              </button>
            );
          }
          return (
            <button
              key={key}
              type="button"
              disabled={disabled}
              onClick={() => press(key)}
              className={cn(
                'inline-flex min-h-16 min-w-16 items-center justify-center rounded-xl',
                'bg-surface-raised text-2xl font-bold text-text',
                'active:scale-[0.97] active:bg-accent active:text-white',
                'disabled:opacity-50',
              )}
              data-testid={`${testIdPrefix}-digit-${key}`}
            >
              {key}
            </button>
          );
        })}
      </div>
    </div>
  );
}
