import { useRef, useState } from 'react';
import { ImagePlus, Loader2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import { CameraCapture, type CameraFacing } from '@/components/ui/CameraCapture';
import { uploadViaPresign, type PresignType } from '@/lib/mediaPresign';
import { cn } from '@/lib/utils';

export type MediaUploadKind = 'AVATAR' | 'PRODUCT' | 'EVIDENCE' | 'LOCATION';

export interface MediaUploadResult {
  id: string;
  contentUrl: string;
  contentType: string;
  byteSize: number;
}

interface MediaPickerProps {
  kind: MediaUploadKind;
  label?: string;
  previewUrl?: string | null;
  /** Show Take photo (device camera → S3 pre-sign path). */
  capture?: boolean;
  /**
   * @deprecated Capture always uses getUserMedia. Kept so existing call sites
   * (`webrtc`) keep compiling; ignored.
   */
  webrtc?: boolean;
  /** Override camera lens; defaults to user for AVATAR, environment otherwise. */
  facingMode?: CameraFacing;
  disabled?: boolean;
  className?: string;
  /** Legacy multipart endpoint (skips pre-sign when set). Still stores in S3 server-side. */
  uploadUrl?: string;
  /** Force pre-sign type; defaults from kind. */
  presignType?: PresignType;
  /** Prefer MinIO/S3 pre-signed PUT (default true when uploadUrl is unset). */
  usePresign?: boolean;
  onUploaded: (result: MediaUploadResult) => void | Promise<void>;
}

function kindToPresign(kind: MediaUploadKind): PresignType {
  switch (kind) {
    case 'AVATAR':
      return 'USER_AVATAR';
    case 'PRODUCT':
      return 'PRODUCT';
    default:
      return 'TRANSACTION';
  }
}

function defaultFacing(kind: MediaUploadKind): CameraFacing {
  return kind === 'AVATAR' ? 'user' : 'environment';
}

export function MediaPicker({
  kind,
  label = 'Add photo',
  previewUrl,
  capture = false,
  facingMode,
  disabled,
  className,
  uploadUrl,
  presignType,
  usePresign,
  onUploaded,
}: MediaPickerProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [phase, setPhase] = useState<'compressing' | 'uploading' | null>(null);
  const [error, setError] = useState('');
  const [localPreview, setLocalPreview] = useState<string | null>(null);

  const preferPresign = usePresign ?? !uploadUrl;

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setError('');
    setBusy(true);
    setPhase(preferPresign ? 'compressing' : 'uploading');
    const preview = URL.createObjectURL(file);
    setLocalPreview(preview);
    try {
      let result: MediaUploadResult;
      if (preferPresign) {
        // Bytes go browser → S3/MinIO PUT → /media/complete (never persisted on API disk).
        const completed = await uploadViaPresign(file, presignType ?? kindToPresign(kind), {
          onPhase: setPhase,
        });
        result = {
          id: completed.id,
          contentUrl: completed.contentUrl,
          contentType: completed.contentType,
          byteSize: completed.byteSize,
        };
        if (kind === 'AVATAR' && result.contentUrl) {
          await apiClient.put('/api/v1/users/me/avatar', { avatarUrl: result.contentUrl });
        }
      } else {
        const body = new FormData();
        body.append('file', file);
        const url = uploadUrl ?? `/api/v1/media/uploads?kind=${encodeURIComponent(kind)}`;
        const res = await apiClient.post<Record<string, unknown>>(url, body, {
          headers: { 'Content-Type': undefined },
        });
        const data = res.data;
        const contentUrl =
          (typeof data.contentUrl === 'string' && data.contentUrl) ||
          (typeof data.avatarUrl === 'string' && data.avatarUrl) ||
          (typeof data.url === 'string' && data.url) ||
          '';
        result = {
          id: String(data.id ?? data.userId ?? ''),
          contentUrl,
          contentType: typeof data.contentType === 'string' ? data.contentType : 'image/*',
          byteSize: typeof data.byteSize === 'number' ? data.byteSize : file.size,
        };
      }
      await onUploaded(result);
    } catch {
      setError('Upload failed — use JPEG, PNG, WebP, or GIF under the size limit.');
      setLocalPreview(null);
    } finally {
      setBusy(false);
      setPhase(null);
      URL.revokeObjectURL(preview);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const busyLabel =
    phase === 'compressing' ? 'Compressing…' : phase === 'uploading' ? 'Uploading…' : label;

  const shown = localPreview ?? previewUrl;

  return (
    <div className={cn('space-y-2', className)} data-testid="media-picker">
      <div className="flex items-start gap-3">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md bg-surface-overlay ring-1 ring-border/60">
          {shown ? (
            localPreview ? (
              <img src={shown} alt="" className="h-full w-full object-cover" />
            ) : (
              <AuthenticatedImage src={shown} alt="" className="h-full w-full object-cover" />
            )
          ) : (
            <ImagePlus className="h-5 w-5 text-text-muted" aria-hidden />
          )}
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={disabled || busy}
              onClick={() => inputRef.current?.click()}
            >
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ImagePlus className="h-4 w-4" />}
              {busyLabel}
            </Button>
            {capture && (
              <CameraCapture
                disabled={disabled || busy}
                label="Take photo"
                facingMode={facingMode ?? defaultFacing(kind)}
                onCapture={(file) => void handleFile(file)}
              />
            )}
          </div>
          <p className="text-xs text-text-muted">
            {phase === 'compressing'
              ? 'Compressing on-device for faster upload…'
              : 'JPEG, PNG, WebP, or GIF. Compressed to WebP before upload.'}
          </p>
          {error && <p className="text-xs text-danger">{error}</p>}
        </div>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        className="sr-only"
        aria-hidden
        tabIndex={-1}
        onChange={(e) => void handleFile(e.target.files?.[0])}
      />
    </div>
  );
}
