import { Fragment, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Activity, ChevronDown, ChevronRight } from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { apiClient } from '@/api/client';
import type { LaborVelocityResponse, LaborVelocityOperator } from '@/api/types';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/utils';

function formatPph(value: number | string | undefined) {
  return Number(value ?? 0).toFixed(1);
}

function OperatorSparkline({ operator }: { operator: LaborVelocityOperator }) {
  const data = (operator.hourlyPicks ?? []).map((p) => ({
    hour: p.hour,
    picks: Number(p.picks),
  }));
  if (data.length === 0) {
    return <p className="text-xs text-text-muted">No hourly pick samples for this shift window.</p>;
  }
  return (
    <div className="h-36 w-full" data-testid={`labor-sparkline-${operator.userId}`}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data}>
          <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
          <XAxis dataKey="hour" className="text-xs" />
          <YAxis allowDecimals={false} className="text-xs" width={28} />
          <Tooltip
            formatter={(value) => [Number(value ?? 0), 'Picks']}
            labelFormatter={(label) => `Hour ${label} UTC`}
          />
          <Area
            type="monotone"
            dataKey="picks"
            stroke="var(--color-accent)"
            fill="var(--color-accent)"
            fillOpacity={0.15}
            strokeWidth={2}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function LaborVelocityLeaderboard() {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard', 'labor-velocity'],
    queryFn: async () =>
      (await apiClient.get<LaborVelocityResponse>('/api/v1/dashboard/labor-velocity')).data,
    retry: false,
    refetchInterval: 60_000,
  });

  const operators = data?.operators ?? [];

  return (
    <section
      className="rounded-2xl bg-surface-raised p-5 shadow-card"
      data-testid="labor-velocity-leaderboard"
    >
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <Activity className="mt-0.5 h-5 w-5 text-accent" aria-hidden />
          <div>
            <h2 className="text-sm font-semibold text-text">Labor Velocity Leaderboard</h2>
            <p className="text-sm text-text-muted">
              Active wave PPH vs strict shift PPH · utilization under 60% highlighted
            </p>
          </div>
        </div>
        {data && (
          <p className="text-xs text-text-muted">
            Warehouse avg active PPH{' '}
            <span className="font-mono font-semibold text-text">
              {formatPph(data.warehouseAvgActivePph)}
            </span>
          </p>
        )}
      </div>

      {isLoading && <TableSkeleton rows={4} cols={5} />}
      {isError && (
        <p className="text-sm text-text-muted">Labor velocity unavailable for this session.</p>
      )}
      {!isLoading && !isError && operators.length === 0 && (
        <p className="text-sm text-text-muted">
          No floor pick / ship activity today yet. Claim a wave and pick to populate the board.
        </p>
      )}
      {!isLoading && operators.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Operator</TableHead>
              <TableHead align="right">Total picks</TableHead>
              <TableHead align="right">Active PPH</TableHead>
              <TableHead align="right">Shift PPH</TableHead>
              <TableHead align="right">Utilization %</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {operators.map((op) => {
              const lowUtil = Number(op.utilizationPercent) < 60;
              const expanded = expandedId === op.userId;
              const delta = Number(op.activePphDeltaVsAvg ?? 0);
              return (
                <Fragment key={op.userId}>
                  <TableRow
                    className="cursor-pointer"
                    onClick={() => setExpandedId(expanded ? null : op.userId)}
                    data-testid={`labor-row-${op.userId}`}
                  >
                    <TableCell>
                      <span className="inline-flex items-center gap-1.5 font-medium text-text">
                        {expanded ? (
                          <ChevronDown className="h-3.5 w-3.5 text-text-muted" />
                        ) : (
                          <ChevronRight className="h-3.5 w-3.5 text-text-muted" />
                        )}
                        {op.operatorName}
                      </span>
                    </TableCell>
                    <TableCell align="right" mono>
                      {op.totalPicks}
                    </TableCell>
                    <TableCell align="right" mono>
                      <span className="inline-flex flex-col items-end">
                        <span>{formatPph(op.activePph)}</span>
                        <span
                          className={cn(
                            'text-[10px]',
                            delta >= 0 ? 'text-success' : 'text-warning',
                          )}
                        >
                          {delta >= 0 ? '+' : ''}
                          {delta.toFixed(1)} vs avg
                        </span>
                      </span>
                    </TableCell>
                    <TableCell align="right" mono>
                      {formatPph(op.shiftPph)}
                    </TableCell>
                    <TableCell align="right">
                      <span
                        className={cn(
                          'inline-flex rounded-md px-2 py-0.5 font-mono text-sm',
                          lowUtil
                            ? 'bg-warning/10 text-warning'
                            : 'bg-success/10 text-success',
                        )}
                      >
                        {Number(op.utilizationPercent).toFixed(1)}%
                      </span>
                    </TableCell>
                  </TableRow>
                  {expanded && (
                    <TableRow>
                      <TableCell colSpan={5} className="bg-surface px-4 py-3">
                        <p className="mb-2 text-xs font-medium text-text-muted">
                          Hourly pick velocity (spot post-lunch slumps)
                        </p>
                        <OperatorSparkline operator={op} />
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              );
            })}
          </TableBody>
        </Table>
      )}
    </section>
  );
}
