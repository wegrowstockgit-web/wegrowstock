import { useCallback, useEffect, useRef, useState } from 'react';
import { Camera, Loader2, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

interface WebRtcCameraProps {
  disabled?: boolean;
  className?: string;
  onCapture: (file: File) => void | Promise<void>;
}

/** Native WebRTC still capture for floor receive / RMA condition photos. */
export function WebRtcCamera({ disabled, className, onCapture }: WebRtcCameraProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  }, []);

  useEffect(() => () => stopStream(), [stopStream]);

  const start = async () => {
    setError('');
    setBusy(true);
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error('Camera API unavailable in this browser');
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      streamRef.current = stream;
      setOpen(true);
      requestAnimationFrame(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          void videoRef.current.play();
        }
      });
    } catch {
      setError('Unable to access camera. Check permissions or use file upload.');
      setOpen(false);
      stopStream();
    } finally {
      setBusy(false);
    }
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

  return (
    <div className={cn('space-y-2', className)} data-testid="webrtc-camera">
      {!open ? (
        <Button type="button" size="sm" variant="secondary" disabled={disabled || busy} onClick={() => void start()}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Camera className="h-4 w-4" />}
          Open camera
        </Button>
      ) : (
        <div className="space-y-2 rounded-md bg-surface-overlay p-2 ring-1 ring-border/60">
          <video
            ref={videoRef}
            playsInline
            muted
            className="aspect-video w-full rounded-sm bg-black object-cover"
            data-testid="webrtc-preview"
          />
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" disabled={busy} onClick={() => void snap()}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Camera className="h-4 w-4" />}
              Snap photo
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              disabled={busy}
              onClick={() => {
                stopStream();
                setOpen(false);
              }}
            >
              <X className="h-4 w-4" />
              Cancel
            </Button>
          </div>
        </div>
      )}
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}
