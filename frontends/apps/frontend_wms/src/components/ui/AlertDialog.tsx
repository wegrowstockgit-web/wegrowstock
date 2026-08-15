import type { ReactNode } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

interface AlertDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirming?: boolean;
  onConfirm: () => void;
  children?: ReactNode;
}

/**
 * Confirm/cancel dialog matching the AlertDialog UX pattern used by shadcn/ui,
 * built on the app's native Modal primitive.
 */
export function AlertDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  confirming = false,
  onConfirm,
  children,
}: AlertDialogProps) {
  return (
    <>
      {children}
      <Modal
        open={open}
        onClose={() => onOpenChange(false)}
        title={title}
        description={description}
      >
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={() => onOpenChange(false)}
            disabled={confirming}
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            variant="danger"
            loading={confirming}
            onClick={onConfirm}
            data-testid="alert-dialog-confirm"
          >
            {confirmLabel}
          </Button>
        </div>
      </Modal>
    </>
  );
}
