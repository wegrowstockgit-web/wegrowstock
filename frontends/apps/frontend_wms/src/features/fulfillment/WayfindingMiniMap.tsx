import { useQuery } from '@tanstack/react-query';
import { Map as MapIcon, X } from 'lucide-react';
import { useState } from 'react';
import { apiClient } from '@/api/client';
import type { WayfindingPath } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { cn } from '@/lib/utils';

interface WayfindingMiniMapProps {
  fromLocationId?: string | null;
  toLocationId?: string | null;
  destinationLabel?: string;
}

const W = 280;
const H = 200;
const PAD = 16;

/**
 * Surface B wayfinding: tap the map icon to open a top-down A* path overlay.
 */
export function WayfindingMiniMap({
  fromLocationId,
  toLocationId,
  destinationLabel,
}: WayfindingMiniMapProps) {
  const [open, setOpen] = useState(false);

  const enabled = open && !!fromLocationId && !!toLocationId;

  const { data, isFetching, isError } = useQuery({
    queryKey: ['picking', 'wayfinding', fromLocationId, toLocationId],
    queryFn: async () =>
      (
        await apiClient.get<WayfindingPath>('/api/v1/picking/wayfinding', {
          params: { fromLocationId, toLocationId },
        })
      ).data,
    enabled,
    staleTime: 15_000,
  });

  if (!toLocationId) return null;

  return (
    <div className="mt-3">
      <Button
        type="button"
        size="sm"
        variant="secondary"
        className="min-h-12 gap-2"
        data-testid="wayfinding-open"
        onClick={() => setOpen(true)}
      >
        <MapIcon className="h-5 w-5" aria-hidden />
        Mini-map
      </Button>

      {open && (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-4 sm:items-center"
          role="dialog"
          aria-modal
          aria-label="Wayfinding map"
          data-testid="wayfinding-overlay"
        >
          <Card className="w-full max-w-md border-2 border-accent p-4" padding="lg">
            <div className="mb-3 flex items-start justify-between gap-2">
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-accent">Wayfinding</p>
                <p className="mt-1 font-mono text-lg font-bold text-text">
                  {destinationLabel ?? 'Next stop'}
                </p>
              </div>
              <button
                type="button"
                className="inline-flex h-10 w-10 items-center justify-center rounded-md border border-border"
                aria-label="Close map"
                data-testid="wayfinding-close"
                onClick={() => setOpen(false)}
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div
              className={cn(
                'overflow-hidden rounded-lg border-2 border-border-strong bg-surface-overlay/60',
              )}
            >
              {isFetching && (
                <p className="p-6 text-center text-sm text-text-muted">Calculating A* path…</p>
              )}
              {isError && (
                <p className="p-6 text-center text-sm text-danger">Could not load wayfinding path</p>
              )}
              {data && !isFetching && <WayfindingSvg path={data} />}
            </div>
            {data && (
              <p className="mt-2 text-xs text-text-muted">
                Travel score {Math.round(data.travelCost)} · {data.points.length} nodes
              </p>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}

function WayfindingSvg({ path }: { path: WayfindingPath }) {
  const points = path.points ?? [];
  if (points.length === 0) {
    return <p className="p-6 text-center text-sm text-text-muted">No path points</p>;
  }

  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const spanX = Math.max(maxX - minX, 1);
  const spanY = Math.max(maxY - minY, 1);

  const project = (x: number, y: number) => ({
    x: PAD + ((x - minX) / spanX) * (W - PAD * 2),
    y: PAD + ((y - minY) / spanY) * (H - PAD * 2),
  });

  const projected = points.map((p) => project(p.x, p.y));
  const poly = projected.map((p) => `${p.x},${p.y}`).join(' ');
  const start = projected[0];
  const end = projected[projected.length - 1];

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} data-testid="wayfinding-svg" className="block">
      <polyline
        points={poly}
        fill="none"
        stroke="currentColor"
        className="text-accent"
        strokeWidth={4}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={start.x} cy={start.y} r={8} className="fill-success" />
      <circle cx={end.x} cy={end.y} r={10} className="fill-accent" />
      <text x={end.x + 12} y={end.y + 4} className="fill-text text-[11px] font-bold">
        {points[points.length - 1]?.code ?? 'DEST'}
      </text>
    </svg>
  );
}
