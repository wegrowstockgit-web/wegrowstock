import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardHeader } from '@/components/ui/Card';

interface PositionFrame {
  id: string;
  tagId: string;
  technology: string;
  x: number;
  y: number;
  z?: number | null;
  assetType?: string;
  assetRef?: string | null;
  observedAt: string;
}

/**
 * React 19 vector workspace for live RTLS tag positions (BLE AoA / UWB).
 */
export function RtlsWorkspacePage() {
  const queryClient = useQueryClient();
  const [tagId, setTagId] = useState('TAG-DEMO-1');
  const [live, setLive] = useState<PositionFrame[]>([]);

  const { data: recent = [] } = useQuery({
    queryKey: ['rtls', 'recent'],
    queryFn: async () =>
      (await apiClient.get<PositionFrame[]>('/api/v1/rtls/positions/recent')).data,
  });

  useEffect(() => {
    setLive(recent);
  }, [recent]);

  useEffect(() => {
    const base = import.meta.env.VITE_API_URL ?? '';
    const es = new EventSource(`${base}/api/v1/rtls/stream`, { withCredentials: true });
    es.addEventListener('rtls.position', (evt) => {
      try {
        const frame = JSON.parse((evt as MessageEvent).data) as PositionFrame;
        setLive((prev) => {
          const next = [frame, ...prev.filter((p) => p.tagId !== frame.tagId)].slice(0, 50);
          return next;
        });
      } catch {
        /* ignore malformed */
      }
    });
    return () => es.close();
  }, []);

  const ingestMutation = useMutation({
    mutationFn: async () => {
      const x = Number((Math.random() * 40).toFixed(3));
      const y = Number((Math.random() * 30).toFixed(3));
      await apiClient.post('/api/v1/rtls/telemetry', {
        packets: [
          {
            tagId,
            technology: 'UWB',
            x,
            y,
            z: 1.2,
            assetType: 'PALLET',
          },
        ],
      });
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['rtls', 'recent'] }),
  });

  const points = useMemo(() => live.slice(0, 20), [live]);

  return (
    <div className="space-y-6 p-6" data-testid="rtls-workspace-page">
      <Card>
        <CardHeader
          title="RTLS vector workspace"
          description="Live BLE AoA / UWB tag positions bound to warehouse assets"
        />
        <div className="flex flex-wrap items-end gap-3">
          <Input label="Tag ID" value={tagId} onChange={(e) => setTagId(e.target.value)} />
          <Button
            type="button"
            onClick={() => ingestMutation.mutate()}
            loading={ingestMutation.isPending}
            data-testid="rtls-inject-sample"
          >
            Inject sample telemetry
          </Button>
        </div>
      </Card>

      <div
        className="relative h-[420px] overflow-hidden rounded-xl border border-border bg-[radial-gradient(circle_at_20%_20%,#e8f4fc,transparent_45%),linear-gradient(#f7fafc,#eef2f7)]"
        data-testid="rtls-vector-canvas"
      >
        <svg viewBox="0 0 100 100" className="h-full w-full">
          {[10, 20, 30, 40, 50, 60, 70, 80, 90].map((n) => (
            <g key={n}>
              <line x1={n} y1={0} x2={n} y2={100} stroke="#cbd5e1" strokeWidth={0.15} />
              <line x1={0} y1={n} x2={100} y2={n} stroke="#cbd5e1" strokeWidth={0.15} />
            </g>
          ))}
          {points.map((p) => {
            const cx = Math.min(95, Math.max(5, Number(p.x) * 2));
            const cy = Math.min(95, Math.max(5, 100 - Number(p.y) * 2));
            return (
              <g key={p.id}>
                <circle cx={cx} cy={cy} r={1.8} fill="#0ea5e9">
                  <animate attributeName="r" values="1.6;2.2;1.6" dur="1.6s" repeatCount="indefinite" />
                </circle>
                <text x={cx + 2.2} y={cy + 0.8} fontSize="2.2" fill="#0f172a">
                  {p.tagId}
                </text>
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
}
