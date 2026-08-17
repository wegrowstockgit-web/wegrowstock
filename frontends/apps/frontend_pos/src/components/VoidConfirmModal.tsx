import { useMemo, useState } from 'react';
import { ScannerPinKeypad } from './ScannerPinKeypad';
import { validateManagerPin } from '@/offline/pinVault';

type VoidConfirmModalProps = {
  open: boolean;
  cartValueLabel: string;
  title: string;
  body: string;
  pinTitle: string;
  pinHint: string;
  invalidPin: string;
  confirmLabel: string;
  cancelLabel: string;
  onCancel: () => void;
  onConfirm: (managerId: string) => void;
};

export function VoidConfirmModal({
  open,
  cartValueLabel,
  title,
  body,
  pinTitle,
  pinHint,
  invalidPin,
  confirmLabel,
  cancelLabel,
  onCancel,
  onConfirm,
}: VoidConfirmModalProps) {
  const [pin, setPin] = useState('');
  const [attempted, setAttempted] = useState(false);

  const managerId = useMemo(() => (pin.length === 4 ? validateManagerPin(pin) : null), [pin]);
  const showError = pin.length === 4 && !managerId;

  if (!open) return null;

  const reset = () => {
    setPin('');
    setAttempted(false);
  };

  return (
    <div className="pos-void-overlay" data-testid="void-confirm-modal" role="dialog" aria-modal="true">
      <div className="pos-void-card">
        <p className="pos-kicker">{cartValueLabel}</p>
        <h1>{title}</h1>
        <p className="pos-void-body">{body}</p>
        <ScannerPinKeypad
          value={pin}
          error={showError || attempted}
          onChange={(next) => {
            setAttempted(false);
            setPin(next);
          }}
          title={pinTitle}
          subtitle={pinHint}
        />
        {showError ? (
          <p className="pos-void-error" data-testid="void-pin-error">
            {invalidPin}
          </p>
        ) : null}
        <div className="pos-void-actions">
          <button
            type="button"
            data-testid="void-confirm-cancel"
            className="pos-void-cancel"
            onClick={() => {
              reset();
              onCancel();
            }}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            data-testid="void-confirm-yes"
            className="pos-void-yes"
            disabled={!managerId}
            onClick={() => {
              const approved = managerId ?? validateManagerPin(pin);
              if (!approved) {
                setAttempted(true);
                return;
              }
              reset();
              onConfirm(approved);
            }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
