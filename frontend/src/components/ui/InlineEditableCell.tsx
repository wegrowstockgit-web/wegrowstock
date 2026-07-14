import { Pencil } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { cn } from '@/lib/utils';

interface InlineEditableCellProps {
  value: string | number;
  onSave: (value: string) => void | Promise<void>;
  disabled?: boolean;
  className?: string;
  inputType?: 'text' | 'number';
  formatDisplay?: (value: string | number) => string;
}

export function InlineEditableCell({
  value,
  onSave,
  disabled = false,
  className,
  inputType = 'text',
  formatDisplay,
}: InlineEditableCellProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(String(value));
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!editing) setDraft(String(value));
  }, [value, editing]);

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  const commit = async () => {
    if (draft === String(value)) {
      setEditing(false);
      return;
    }
    setSaving(true);
    try {
      await onSave(draft);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  };

  if (editing) {
    return (
      <input
        ref={inputRef}
        type={inputType}
        value={draft}
        disabled={saving}
        aria-label="Edit value"
        onChange={(e) => setDraft(e.target.value)}
        onBlur={() => void commit()}
        onKeyDown={(e) => {
          if (e.key === 'Enter') void commit();
          if (e.key === 'Escape') {
            setDraft(String(value));
            setEditing(false);
          }
        }}
        className={cn(
          'w-full rounded border border-accent bg-surface-raised px-2 py-1 text-sm font-mono tabular-nums outline-none focus:ring-2 focus:ring-accent/30',
          className
        )}
      />
    );
  }

  return (
    <button
      type="button"
      disabled={disabled}
      onDoubleClick={() => !disabled && setEditing(true)}
      className={cn(
        'group inline-flex w-full items-center justify-end gap-1 rounded px-1 py-0.5 text-right font-mono tabular-nums transition-colors',
        !disabled && 'hover:bg-surface-overlay',
        className
      )}
      title={disabled ? undefined : 'Double-click to edit'}
      aria-label={disabled ? undefined : `Edit value ${formatDisplay ? formatDisplay(value) : value}`}
    >
      <span>{formatDisplay ? formatDisplay(value) : value}</span>
      {!disabled && (
        <Pencil
          className="h-3 w-3 shrink-0 text-text-muted opacity-0 transition-opacity group-hover:opacity-100"
          aria-hidden
        />
      )}
    </button>
  );
}
