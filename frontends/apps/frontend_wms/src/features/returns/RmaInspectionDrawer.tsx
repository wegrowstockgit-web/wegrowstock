import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Camera } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { ReturnLine, RmaQcDisposition, RmaQcGrade } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { SlideOutDrawer } from '@/components/ui/SlideOutDrawer';
import { cn } from '@/lib/utils';

const GRADES: Array<{ value: RmaQcGrade; label: string; description: string }> = [
  { value: 'GRADE_A_NEW', label: 'Grade A — New', description: 'Resellable, factory condition' },
  {
    value: 'GRADE_B_OPEN_BOX',
    label: 'Grade B — Open box',
    description: 'Opened packaging, functional',
  },
  { value: 'GRADE_C_DAMAGED', label: 'Grade C — Damaged', description: 'Visible damage or defect' },
];

const DISPOSITIONS: Array<{ value: RmaQcDisposition; label: string }> = [
  { value: 'RESTOCK', label: 'Restock' },
  { value: 'SCRAP', label: 'Scrap' },
  { value: 'REPAIR', label: 'Repair' },
  { value: 'REFURBISH', label: 'Refurbish' },
];

export interface RmaInspectionDrawerProps {
  open: boolean;
  onClose: () => void;
  returnId: string;
  line: ReturnLine | null;
}

export function RmaInspectionDrawer({ open, onClose, line }: RmaInspectionDrawerProps) {
  const queryClient = useQueryClient();
  const [grade, setGrade] = useState<RmaQcGrade>('GRADE_A_NEW');
  const [disposition, setDisposition] = useState<RmaQcDisposition>('RESTOCK');
  const [notes, setNotes] = useState('');
  const [attachmentIds, setAttachmentIds] = useState<string[]>([]);
  const [saveError, setSaveError] = useState<string | null>(null);

  const lineId = line?.id ?? null;

  useEffect(() => {
    if (!open) {
      setGrade('GRADE_A_NEW');
      setDisposition('RESTOCK');
      setNotes('');
      setAttachmentIds([]);
      setSaveError(null);
    }
  }, [open]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!lineId) return;
      const uuidAttachments = attachmentIds.filter((id) =>
        /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id),
      );
      const localNotes = attachmentIds
        .filter((id) => id.startsWith('local:'))
        .map((id) => id.replace(/^local:/, ''))
        .join(', ');
      const combinedNotes = [notes.trim(), localNotes ? `Photos: ${localNotes}` : '']
        .filter(Boolean)
        .join('\n');
      await apiClient.post('/api/v1/returns/qc/inspections', {
        returnLineId: lineId,
        grade,
        dispositionAction: disposition,
        inspectionNotes: combinedNotes || undefined,
        photoAttachmentIds: uuidAttachments,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['returns'] });
      onClose();
    },
    onError: () => {
      setSaveError('Could not save QC inspection.');
    },
  });

  const handlePhotoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length === 0) return;
    const ids = files.map((file) => `local:${file.name}:${file.size}`);
    setAttachmentIds((prev) => [...prev, ...ids]);
    e.target.value = '';
  };

  return (
    <SlideOutDrawer
      open={open}
      onClose={onClose}
      title="RMA QC inspection"
      description={line ? `${line.sku ?? line.productName ?? line.id}` : undefined}
      width="lg"
    >
      <div className="space-y-6" data-testid="rma-inspection-drawer">
        <fieldset>
          <legend className="text-sm font-semibold text-text">Grade</legend>
          <div className="mt-2 space-y-2">
            {GRADES.map((option) => (
              <label
                key={option.value}
                className={cn(
                  'flex cursor-pointer items-start gap-3 rounded-md border px-3 py-2',
                  grade === option.value
                    ? 'border-accent bg-accent-muted'
                    : 'border-border bg-surface-raised',
                )}
              >
                <input
                  type="radio"
                  name="rma-grade"
                  value={option.value}
                  checked={grade === option.value}
                  onChange={() => setGrade(option.value)}
                  className="mt-1"
                  data-testid={`grade-${option.value}`}
                />
                <span>
                  <span className="block text-sm font-medium text-text">{option.label}</span>
                  <span className="block text-xs text-text-muted">{option.description}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <div>
          <label htmlFor="rma-disposition" className="text-sm font-semibold text-text">
            Disposition
          </label>
          <select
            id="rma-disposition"
            value={disposition}
            onChange={(e) => setDisposition(e.target.value as RmaQcDisposition)}
            className="mt-2 h-10 w-full rounded-md border border-border bg-surface-raised px-3 text-sm text-text"
            data-testid="rma-disposition-select"
          >
            {DISPOSITIONS.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </select>
        </div>

        <Input
          label="Inspection notes"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Optional condition notes…"
          data-testid="rma-inspection-notes"
        />

        <div>
          <p className="text-sm font-semibold text-text">Evidence photos</p>
          <p className="mt-0.5 text-xs text-text-muted">
            Attachment IDs stored locally until media upload is wired.
          </p>
          <label className="mt-3 inline-flex cursor-pointer items-center gap-2 rounded-md border border-dashed border-border px-4 py-3 text-sm text-text-muted hover:border-accent hover:text-text">
            <Camera className="h-4 w-4" />
            Add photos
            <input
              type="file"
              accept="image/*"
              multiple
              className="sr-only"
              onChange={handlePhotoChange}
              data-testid="rma-photo-input"
            />
          </label>
          {attachmentIds.length > 0 && (
            <ul className="mt-3 space-y-2">
              {attachmentIds.map((id) => (
                <li
                  key={id}
                  className="flex items-center justify-between rounded-md border border-border bg-surface-overlay px-3 py-2 text-xs font-mono"
                >
                  <span className="truncate">{id}</span>
                  <button
                    type="button"
                    className="text-danger hover:underline"
                    onClick={() => setAttachmentIds((prev) => prev.filter((x) => x !== id))}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {saveError && <p className="text-sm text-danger">{saveError}</p>}

        <div className="flex justify-end gap-2 border-t border-border pt-4">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="button"
            loading={saveMutation.isPending}
            disabled={!lineId}
            data-testid="save-rma-inspection"
            onClick={() => saveMutation.mutate()}
          >
            Save inspection
          </Button>
        </div>
      </div>
    </SlideOutDrawer>
  );
}
