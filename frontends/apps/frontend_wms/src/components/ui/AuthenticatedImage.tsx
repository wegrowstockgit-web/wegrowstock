import { useEffect, useState } from 'react';
import { apiClient } from '@/api/client';
import { cn } from '@/lib/utils';

function isAppMediaPath(src: string): boolean {
  return src.startsWith('/api/v1/media/') && src.includes('/content');
}

/** Loads tenant-scoped media with JWT when src is an authenticated API path. */
export function AuthenticatedImage({
  src,
  alt,
  className,
  onError,
}: {
  src?: string | null;
  alt: string;
  className?: string;
  onError?: () => void;
}) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!src?.trim()) {
      setObjectUrl(null);
      return;
    }
    if (!isAppMediaPath(src)) {
      setObjectUrl(src);
      return;
    }

    let revoked: string | null = null;
    let cancelled = false;
    void apiClient
      .get<Blob>(src, { responseType: 'blob' })
      .then((res) => {
        if (cancelled) return;
        const url = URL.createObjectURL(res.data);
        revoked = url;
        setObjectUrl(url);
      })
      .catch(() => {
        if (!cancelled) {
          setObjectUrl(null);
          onError?.();
        }
      });

    return () => {
      cancelled = true;
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [src, onError]);

  if (!objectUrl) return null;

  return (
    <img
      src={objectUrl}
      alt={alt}
      className={cn(className)}
      onError={() => {
        setObjectUrl(null);
        onError?.();
      }}
    />
  );
}
