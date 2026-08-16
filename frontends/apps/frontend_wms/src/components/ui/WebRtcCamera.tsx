import { useCallback, useEffect, useRef, useState } from 'react';
import { Camera, Loader2, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { decodeBarcodeFromVideo } from '@/lib/barcodeCameraDecode';
import { cn } from '@/lib/utils';

export interface WebRtcCameraProps {
  className?: string;
  /** Open the live preview immediately when mounted. */
  autoStart?: boolean;
  onBarcode: (barcode: string) => void;
  onCancel?: () => void;
}

/**
 * Live device-camera barcode decoder (BarcodeDetector, then ZXing).
 * Photo stills stay on {@link CameraCapture}.
 */
export function WebRtcCamera({
  className,
  autoStart = true,
  onBarcode,
  onCancel,
}: WebRtcCameraProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const onBarcodeRef = useRef(onBarcode);
  const onCancelRef = useRef(onCancel);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    onBarcodeRef.current = onBarcode;
  }, [onBarcode]);

  useEffect(() => {
    onCancelRef.current = onCancel;
  }, [onCancel]);

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  }, []);

  useEffect(() => {
    if (!autoStart) return undefined;
    let cancelled = false;
    setBusy(true);
    setError('');

    const start = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error('Camera API unavailable in this browser');
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1280 },
          height: { ideal: 720 },
        },
        audio: false,
      });
      if (cancelled) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
    };

    void start()
      .catch(() => {
        if (!cancelled) {
          setError('Unable to access camera. Use Keyboard Entry instead.');
        }
      })
      .finally(() => {
        if (!cancelled) setBusy(false);
      });

    return () => {
      cancelled = true;
      stopStream();
    };
  }, [autoStart, stopStream]);

  useEffect(() => {
    if (busy || error || !autoStart) return undefined;
    let cancelled = false;
    let timer: ReturnType<typeof setInterval> | undefined;

    let inFlight = false;
    const tick = async () => {
      const video = videoRef.current;
      if (cancelled || inFlight || !video) return;
      inFlight = true;
      try {
        const value = await decodeBarcodeFromVideo(video);
        if (cancelled || !value) return;
        stopStream();
        onBarcodeRef.current(value);
      } finally {
        inFlight = false;
      }
    };

    timer = setInterval(() => {
      void tick();
    }, 250);

    return () => {
      cancelled = true;
      if (timer) clearInterval(timer);
    };
  }, [autoStart, busy, error, stopStream]);

  const close = () => {
    stopStream();
    onCancelRef.current?.();
  };

  return (
    <div className={cn('space-y-2', className)} data-testid="webrtc-camera">
      <div
        className="relative overflow-hidden rounded-xl bg-black ring-1 ring-white/20"
        data-testid="webrtc-camera-live"
      >
        <video
          ref={videoRef}
          playsInline
          muted
          autoPlay
          className="aspect-video w-full bg-black object-cover"
          data-testid="webrtc-camera-preview"
        />
        <div
          className="pointer-events-none absolute inset-x-[12%] top-1/2 h-0.5 -translate-y-1/2 bg-accent/90 shadow-[0_0_12px_var(--color-accent)]"
          aria-hidden
        />
        {busy && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/50">
            <Loader2 className="h-8 w-8 animate-spin text-accent" aria-hidden />
            <span className="sr-only">Starting camera</span>
          </div>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={close}
          className="text-white hover:bg-white/10 active:scale-[0.97]"
          data-testid="webrtc-camera-close"
        >
          <X className="h-4 w-4" />
          Close camera
        </Button>
      </div>
      {error && (
        <p className="text-xs text-warning" data-testid="webrtc-camera-error">
          {error}
        </p>
      )}
      {!error && (
        <p className="flex items-center justify-center gap-1.5 text-xs text-white/70">
          <Camera className="h-3.5 w-3.5" aria-hidden />
          Point the lens at the barcode
        </p>
      )}
    </div>
  );
}

export type { CameraFacing } from '@/components/ui/CameraCapture';
