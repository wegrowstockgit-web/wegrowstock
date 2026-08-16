import { useCallback, useEffect, useRef, useState } from 'react';
import { Camera, Loader2, RefreshCw, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

export type CameraFacing = 'user' | 'environment';

export interface CameraCaptureProps {
  disabled?: boolean;
  className?: string;
  /** Button label when idle (default: Take photo). */
  label?: string;
  /** Preferred lens; user-facing for avatars, environment for warehouse. */
  facingMode?: CameraFacing;
  /** Open the live preview immediately when mounted. */
  autoStart?: boolean;
  onCapture: (file: File) => void | Promise<void>;
  onCancel?: () => void;
}

/**
 * Device-camera still capture via getUserMedia (laptop webcam / phone).
 * Produces a JPEG File for the shared S3 pre-sign upload path — never writes to disk.
 */
export function CameraCapture({
  disabled,
  className,
  label = 'Take photo',
  facingMode = 'environment',
  autoStart = false,
  onCapture,
  onCancel,
}: CameraCaptureProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [open, setOpen] = useState(autoStart);
  const [facing, setFacing] = useState<CameraFacing>(facingMode);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  }, []);

  const attachStream = useCallback(async (mode: CameraFacing) => {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('Camera API unavailable in this browser');
    }
    stopStream();
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: mode },
        width: { ideal: 1280 },
        height: { ideal: 720 },
      },
      audio: false,
    });
    streamRef.current = stream;
    if (videoRef.current) {
      videoRef.current.srcObject = stream;
      await videoRef.current.play();
    }
  }, [stopStream]);

  useEffect(() => () => stopStream(), [stopStream]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setBusy(true);
    setError('');
    void attachStream(facing)
      .catch(() => {
        if (!cancelled) {
          setError('Unable to access camera. Check permissions or use Upload photo.');
          setOpen(false);
          stopStream();
          onCancel?.();
        }
      })
      .finally(() => {
        if (!cancelled) setBusy(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, facing, attachStream, stopStream, onCancel]);

  const start = () => {
    setError('');
    setOpen(true);
  };

  const close = () => {
    stopStream();
    setOpen(false);
    onCancel?.();
  };

  const snap = async () => {
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) return;
    setBusy(true);
    try {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('Canvas unavailable');
      ctx.drawImage(video, 0, 0);
      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', 0.92),
      );
      if (!blob) throw new Error('Capture failed');
      const file = new File([blob], `capture-${Date.now()}.jpg`, { type: 'image/jpeg' });
      await onCapture(file);
      stopStream();
      setOpen(false);
    } catch {
      setError('Capture failed — try again.');
    } finally {
      setBusy(false);
    }
  };

  const flip = () => {
    setFacing((prev) => (prev === 'user' ? 'environment' : 'user'));
  };

  return (
    <div className={cn('space-y-2', className)} data-testid="camera-capture">
      {!open ? (
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={disabled || busy}
          onClick={start}
          data-testid="camera-capture-open"
        >
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Camera className="h-4 w-4" />}
          {label}
        </Button>
      ) : (
        <div
          className="space-y-2 rounded-md bg-surface-overlay p-2 ring-1 ring-border/60"
          data-testid="camera-capture-live"
        >
          <video
            ref={videoRef}
            playsInline
            muted
            autoPlay
            className="aspect-video w-full rounded-sm bg-black object-cover"
            data-testid="camera-capture-preview"
          />
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              size="sm"
              disabled={busy}
              onClick={() => void snap()}
              data-testid="camera-capture-snap"
            >
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Camera className="h-4 w-4" />}
              Snap photo
            </Button>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={flip}
              aria-label="Switch camera"
              data-testid="camera-capture-flip"
            >
              <RefreshCw className="h-4 w-4" />
              Flip
            </Button>
            <Button type="button" size="sm" variant="ghost" disabled={busy} onClick={close}>
              <X className="h-4 w-4" />
              Cancel
            </Button>
          </div>
        </div>
      )}
      {error && (
        <p className="text-xs text-danger" data-testid="camera-capture-error">
          {error}
        </p>
      )}
    </div>
  );
}
