import { useState, type DragEvent } from 'react';
import { ImagePlus, Loader2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { uploadViaPresign } from '@/lib/mediaPresign';
import { compressImageForUpload } from '@/utils/imageCompression';
import { cn } from '@/lib/utils';

interface ProductMediaDropZoneProps {
  variantId: string;
  disabled?: boolean;
  className?: string;
  onUploaded?: () => void | Promise<void>;
}

/** Drag-and-drop bulk catalog uploads via pre-signed MinIO PUTs. */
export function ProductMediaDropZone({
  variantId,
  disabled,
  className,
  onUploaded,
}: ProductMediaDropZoneProps) {
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);
  const [phase, setPhase] = useState<'compressing' | 'uploading' | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const uploadFiles = async (files: FileList | File[]) => {
    const list = Array.from(files).filter((f) => f.type.startsWith('image/'));
    if (list.length === 0) {
      setError('Drop image files only (JPEG, PNG, WebP, GIF).');
      return;
    }
    setError('');
    setMessage('');
    setBusy(true);
    let ok = 0;
    try {
      setPhase('compressing');
      // Concurrent client-thread compression before any pre-sign round-trips.
      const compressed = await Promise.all(list.map((file) => compressImageForUpload(file)));
      setPhase('uploading');
      for (let i = 0; i < compressed.length; i++) {
        const file = compressed[i]!;
        const completed = await uploadViaPresign(file, 'PRODUCT', { compress: false });
        await apiClient.post(`/api/v1/products/variants/${variantId}/media`, {
          url: completed.contentUrl,
          isPrimary: i === 0,
          sortOrder: i,
        });
        ok += 1;
      }
      setMessage(`Uploaded ${ok} photo${ok === 1 ? '' : 's'}`);
      await onUploaded?.();
    } catch {
      setError(ok > 0 ? `Uploaded ${ok}, then failed` : 'Upload failed');
    } finally {
      setBusy(false);
      setPhase(null);
      setDragging(false);
    }
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    if (disabled || busy) return;
    void uploadFiles(e.dataTransfer.files);
  };

  return (
    <div
      data-testid="product-media-dropzone"
      className={cn(
        'rounded-md border border-dashed px-3 py-4 text-center transition-colors',
        dragging ? 'border-accent bg-accent/5' : 'border-border bg-surface-overlay/40',
        (disabled || busy) && 'opacity-60',
        className,
      )}
      onDragEnter={(e) => {
        e.preventDefault();
        if (!disabled) setDragging(true);
      }}
      onDragOver={(e) => e.preventDefault()}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <div className="flex flex-col items-center gap-1 text-sm text-text-muted">
        {busy ? (
          <Loader2 className="h-5 w-5 animate-spin text-accent" />
        ) : (
          <ImagePlus className="h-5 w-5" aria-hidden />
        )}
        <p className="font-medium text-text">Drop product photos here</p>
        <p className="text-xs">
          {phase === 'compressing'
            ? 'Compressing images locally…'
            : phase === 'uploading'
              ? 'Uploading to secure storage…'
              : 'Bulk upload via secure MinIO pre-signed URLs'}
        </p>
        {message && <p className="text-xs text-success">{message}</p>}
        {error && <p className="text-xs text-danger">{error}</p>}
      </div>
    </div>
  );
}
