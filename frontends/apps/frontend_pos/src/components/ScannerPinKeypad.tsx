const DIGITS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'back'] as const;

type ScannerPinKeypadProps = {
  value: string;
  maxLength?: number;
  error?: boolean;
  disabled?: boolean;
  onChange: (next: string) => void;
  title: string;
  subtitle?: string;
  testIdPrefix?: string;
};

export function ScannerPinKeypad({
  value,
  maxLength = 4,
  error = false,
  disabled = false,
  onChange,
  title,
  subtitle,
  testIdPrefix = 'scanner-pin',
}: ScannerPinKeypadProps) {
  const press = (digit: string) => {
    if (disabled || value.length >= maxLength) return;
    onChange(value + digit);
  };

  const backspace = () => {
    if (disabled) return;
    onChange(value.slice(0, -1));
  };

  return (
    <div className="pos-pin-keypad" data-testid={`${testIdPrefix}-keypad`}>
      <div className="pos-pin-copy">
        <h2>{title}</h2>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
      <div
        className={`pos-pin-dots${error ? ' is-error' : ''}`}
        data-testid={`${testIdPrefix}-dots`}
        aria-label={`${value.length} of ${maxLength} digits entered`}
      >
        {Array.from({ length: maxLength }, (_, index) => (
          <span key={index} data-filled={index < value.length ? 'true' : 'false'} />
        ))}
      </div>
      <div className="pos-pin-grid">
        {DIGITS.map((key, index) => {
          if (key === '') return <div key={`spacer-${index}`} />;
          if (key === 'back') {
            return (
              <button
                key="back"
                type="button"
                disabled={disabled}
                onClick={backspace}
                data-testid={`${testIdPrefix}-back`}
                aria-label="Delete last digit"
              >
                ⌫
              </button>
            );
          }
          return (
            <button
              key={key}
              type="button"
              disabled={disabled}
              onClick={() => press(key)}
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
