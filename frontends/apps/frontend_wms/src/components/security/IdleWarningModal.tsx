import { Button } from '@/components/ui/Button';

type IdleWarningModalProps = {
  open: boolean;
  onStaySignedIn: () => void;
};

export function IdleWarningModal({ open, onStaySignedIn }: IdleWarningModalProps) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-black/40 p-4"
      data-testid="idle-warning-modal"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="idle-warning-title"
      aria-describedby="idle-warning-copy"
    >
      <div className="w-full max-w-md rounded-xl border border-border bg-surface-raised p-6 shadow-elevated">
        <h2 id="idle-warning-title" className="text-lg font-semibold text-text">
          Still there?
        </h2>
        <p id="idle-warning-copy" className="mt-2 text-sm text-text-muted">
          Your session will lock in 2 minutes due to inactivity.
        </p>
        <div className="mt-5 flex justify-end">
          <Button type="button" onClick={onStaySignedIn} data-testid="idle-keep-signed-in">
            Keep me signed in
          </Button>
        </div>
      </div>
    </div>
  );
}
