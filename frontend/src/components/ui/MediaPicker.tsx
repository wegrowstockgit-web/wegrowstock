import { useRef, useState } from 'react';
import { Camera, ImagePlus, Loader2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { AuthenticatedImage } from '@/components/ui/AuthenticatedImage';
import { WebRtcCamera } from '@/components/ui/WebRtcCamera';
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
  capture?: boolean;
  /** Use WebRTC getUserMedia instead of input capture= (floor surfaces). */
  webrtc?: boolean;
  disabled?: boolean;
  className?: string;
  /** Legacy multipart endpoint (skips pre-sign when set). */
  uploadUrl?: string;
  /** Force pre-sign type; defaults from kind. */
  presignType?: PresignType;
  /** Prefer MinIO pre-signed PUT (default true when uploadUrl is unset). */
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

export function MediaPicker({
  kind,
  label = 'Add photo',
  previewUrl,
  capture = false,
  webrtc = false,
  disabled,
  className,
  uploadUrl,
  presignType,
  usePresign,
  onUploaded,
}: MediaPickerProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [localPreview, setLocalPreview] = useState<string | null>(null);

  const preferPresign = usePresign ?? !uploadUrl;

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    setError('');
    setBusy(true);
    const preview = URL.createObjectURL(file);
    setLocalPreview(preview);
    try {
      let result: MediaUploadResult;
      if (preferPresign) {
        const completed = await uploadViaPresign(file, presignType ?? kindToPresign(kind));
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
      URL.revokeObjectURL(preview);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const shown = localPreview ?? previewUrl;

  return (
    <div className={cn('space-y-2', className)} data-testid="media-picker">
      <div className="flex items-start gap-3">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md bg-surface-overlay ring-1 ring-border/60">
          {shown ? (
            localPreview ? (
              <img src={localPreview} alt="" className="h-full w-full object-cover" />
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
              onClick={() => {
                if (inputRef.current) {
                  inputRef.current.removeAttribute('capture');
                  inputRef.current.click();
                }
              }}
            >
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ImagePlus className="h-4 w-4" />}
              {label}
            </Button>
            {capture && !webrtc && (
              <Button
                type="button"
                size="sm"
                variant="secondary"
                disabled={disabled || busy}
                onClick={() => {
                  if (inputRef.current) {
                    inputRef.current.setAttribute('capture', 'environment');
                    inputRef.current.click();
                  }
                }}
              >
                <Camera className="h-4 w-4" />
                Take photo
              </Button>
            )}
          </div>
          {webrtc && (
            <WebRtcCamera disabled={disabled || busy} onCapture={(file) => void handleFile(file)} />
          )}
          <p className="text-xs text-text-muted">JPEG, PNG, WebP, or GIF. Stored privately per tenant.</p>
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
