import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

interface HeatmapCell {
  locationId: string;
  code: string;
  path: string;
  coordX?: number | null;
  coordY?: number | null;
  movementCount: number;
  intensity: number;
}

interface DigitalTwinMapProps {
  locations: TenantLocation[];
}

const CELL = 28;
const PAD = 24;

function intensityColor(intensity: number, heatmap: boolean): string {
  if (!heatmap) return 'var(--color-accent, #2563eb)';
  // Blue (cold) → red (hot)
  const t = Math.max(0, Math.min(1, intensity));
  const r = Math.round(40 + t * 200);
  const g = Math.round(90 - t * 70);
  const b = Math.round(220 - t * 180);
  return `rgb(${r},${g},${b})`;
}

/**
 * Interactive Digital Twin floor plan — drag bins to update coord_x / coord_y.
 */
export function DigitalTwinMap({ locations }: DigitalTwinMapProps) {
  const queryClient = useQueryClient();
  const svgRef = useRef<SVGSVGElement>(null);
  const [heatmapOn, setHeatmapOn] = useState(false);
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<Record<string, { x: number; y: number }>>({});

  const bins = useMemo(
    () =>
      locations.filter(
        (l) =>
          l.type === 'BIN' ||
          l.type === 'AISLE' ||
          l.type === 'ZONE' ||
          l.type === 'WAREHOUSE',
      ),
    [locations],
  );

  const { data: heatmap = [] } = useQuery({
    queryKey: ['locations', 'heatmap'],
    queryFn: async () =>
      (await apiClient.get<HeatmapCell[]>('/api/v1/locations/heatmap', { params: { days: 7 } }))
        .data,
    enabled: heatmapOn,
    staleTime: 30_000,
  });

  const heatById = useMemo(() => {
    const map = new Map<string, HeatmapCell>();
    for (const cell of heatmap) map.set(cell.locationId, cell);
    return map;
  }, [heatmap]);

  const positioned = useMemo(() => {
    return bins.map((loc, idx) => {
      const d = draft[loc.id];
      const x =
        d?.x ??
        (loc.coordX != null ? Number(loc.coordX) : (idx % 12) * 10);
      const y =
        d?.y ??
        (loc.coordY != null ? Number(loc.coordY) : Math.floor(idx / 12) * 10);
      return { loc, x, y };
    });
  }, [bins, draft]);

  const bounds = useMemo(() => {
    let maxX = 40;
    let maxY = 40;
    for (const p of positioned) {
      maxX = Math.max(maxX, p.x + 10);
      maxY = Math.max(maxY, p.y + 10);
    }
    return { maxX, maxY };
  }, [positioned]);

  const saveMutation = useMutation({
    mutationFn: async (input: { id: string; x: number; y: number }) => {
      await apiClient.patch(`/api/v1/locations/${input.id}/coordinates`, {
        coordX: input.x,
        coordY: input.y,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locations'] });
      void queryClient.invalidateQueries({ queryKey: ['locations', 'heatmap'] });
    },
  });

  useEffect(() => {
    if (!draggingId) return;
    const onUp = () => {
      const d = draft[draggingId];
      if (d) {
        saveMutation.mutate({ id: draggingId, x: d.x, y: d.y });
      }
      setDraggingId(null);
    };
    window.addEventListener('pointerup', onUp);
    return () => window.removeEventListener('pointerup', onUp);
  }, [draggingId, draft, saveMutation]);

  const toWorld = (clientX: number, clientY: number) => {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0 };
    const rect = svg.getBoundingClientRect();
    const x = ((clientX - rect.left - PAD) / CELL);
    const y = ((clientY - rect.top - PAD) / CELL);
    return { x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
  };

  return (
    <div className="space-y-3" data-testid="digital-twin-map">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-text-muted">
          Digital Twin — drag bins to reposition. Coordinates save on drop.
        </p>
        <Button
          type="button"
          size="sm"
          variant={heatmapOn ? 'primary' : 'secondary'}
          data-testid="heatmap-toggle"
          onClick={() => setHeatmapOn((v) => !v)}
        >
          {heatmapOn ? 'Heatmap on' : 'Heatmap off'}
        </Button>
      </div>

      <div className="overflow-auto rounded-lg border-2 border-border-strong bg-surface-overlay/40">
        <svg
          ref={svgRef}
          data-testid="digital-twin-svg"
          width={PAD * 2 + bounds.maxX * CELL}
          height={PAD * 2 + bounds.maxY * CELL}
          className="block touch-none"
          onPointerMove={(e) => {
            if (!draggingId) return;
            const { x, y } = toWorld(e.clientX, e.clientY);
            setDraft((prev) => ({ ...prev, [draggingId]: { x, y } }));
          }}
        >
          {/* Grid */}
          {Array.from({ length: Math.ceil(bounds.maxX) + 1 }).map((_, i) => (
            <line
              key={`vx-${i}`}
              x1={PAD + i * CELL}
              y1={PAD}
              x2={PAD + i * CELL}
              y2={PAD + bounds.maxY * CELL}
              stroke="currentColor"
              className="text-border"
              strokeWidth={0.5}
            />
          ))}
          {Array.from({ length: Math.ceil(bounds.maxY) + 1 }).map((_, i) => (
            <line
              key={`hy-${i}`}
              x1={PAD}
              y1={PAD + i * CELL}
              x2={PAD + bounds.maxX * CELL}
              y2={PAD + i * CELL}
              stroke="currentColor"
              className="text-border"
              strokeWidth={0.5}
            />
          ))}

          {positioned.map(({ loc, x, y }) => {
            const heat = heatById.get(loc.id);
            const fill = intensityColor(heat?.intensity ?? 0, heatmapOn && loc.type === 'BIN');
            const size = loc.type === 'BIN' ? CELL - 6 : CELL - 2;
            return (
              <g
                key={loc.id}
                transform={`translate(${PAD + x * CELL}, ${PAD + y * CELL})`}
                data-testid={`twin-node-${loc.code}`}
                className={cn(loc.type === 'BIN' && 'cursor-grab', draggingId === loc.id && 'cursor-grabbing')}
                onPointerDown={(e) => {
                  if (loc.type !== 'BIN') return;
                  e.currentTarget.setPointerCapture(e.pointerId);
                  setDraggingId(loc.id);
                }}
              >
                <rect
                  width={size}
                  height={size}
                  rx={4}
                  fill={fill}
                  opacity={loc.type === 'BIN' ? 0.9 : 0.35}
                  stroke="currentColor"
                  className="text-text"
                  strokeWidth={1.5}
                />
                <text
                  x={size / 2}
                  y={size / 2 + 3}
                  textAnchor="middle"
                  className="fill-text-inverse text-[9px] font-bold"
                  style={{ fill: heatmapOn && loc.type === 'BIN' ? '#fff' : undefined }}
                >
                  {loc.code}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
}
