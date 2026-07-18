import { Fragment, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { Button } from '@/components/ui/Button';
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
import { useSessionStore } from '@/stores/session';

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

function useLaborVelocity() {
  return useQuery({
    queryKey: ['dashboard', 'labor-velocity'],
    queryFn: async () =>
      (await apiClient.get<LaborVelocityResponse>('/api/v1/dashboard/labor-velocity')).data,
    retry: false,
    refetchInterval: 60_000,
  });
}

function FullLeaderboardTable({ operators }: { operators: LaborVelocityOperator[] }) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
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
                      className={cn('text-[10px]', delta >= 0 ? 'text-success' : 'text-warning')}
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
                      lowUtil ? 'bg-warning/10 text-warning' : 'bg-success/10 text-success',
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
  );
}

export function LaborVelocityLeaderboard({
  mode = 'full',
}: {
  /** summary = dashboard card (top 3 + you); full = reports workspace */
  mode?: 'summary' | 'full';
}) {
  const navigate = useNavigate();
  const userId = useSessionStore((s) => s.user?.id);
  const { data, isLoading, isError, refetch } = useLaborVelocity();
  const operators = data?.operators ?? [];

  const summaryRows = useMemo(() => {
    const ranked = [...operators].sort(
      (a, b) => Number(b.activePph) - Number(a.activePph) || Number(b.totalPicks) - Number(a.totalPicks),
    );
    const top3 = ranked.slice(0, 3);
    const me = userId ? ranked.find((o) => o.userId === userId) : undefined;
    const meInTop = me ? top3.some((o) => o.userId === me.userId) : true;
    return { top3, me, meRank: me ? ranked.findIndex((o) => o.userId === me.userId) + 1 : null, meInTop };
  }, [operators, userId]);

  if (mode === 'summary') {
    return (
      <section
        className="flex h-64 flex-col rounded-2xl bg-surface-raised p-5 shadow-card"
        data-testid="labor-velocity-leaderboard"
        data-mode="summary"
      >
        <div className="mb-3 flex items-start justify-between gap-2">
          <div className="flex items-start gap-2">
            <Activity className="mt-0.5 h-5 w-5 text-accent" aria-hidden />
            <div>
              <h2 className="text-sm font-semibold text-text">Labor velocity</h2>
              <p className="text-xs text-text-muted">Top 3 pickers · active PPH</p>
            </div>
          </div>
          {data && (
            <p className="text-xs text-text-muted">
              Avg{' '}
              <span className="font-mono font-semibold text-text">
                {formatPph(data.warehouseAvgActivePph)}
              </span>
            </p>
          )}
        </div>

        <div className="min-h-0 flex-1 overflow-hidden">
          {isLoading && (
            <div data-testid="list-page-loading">
              <TableSkeleton rows={3} cols={2} />
            </div>
          )}
          {isError && (
            <p className="text-sm text-text-muted" data-testid="list-page-error">
              Labor velocity unavailable.{' '}
              <button type="button" className="text-accent hover:underline" onClick={() => void refetch()}>
                Retry
              </button>
            </p>
          )}
          {!isLoading && !isError && operators.length === 0 && (
            <p className="text-sm text-text-muted" data-testid="list-page-empty">
              No floor pick activity today yet.
            </p>
          )}
          {!isLoading && !isError && summaryRows.top3.length > 0 && (
            <ol className="space-y-2">
              {summaryRows.top3.map((op, idx) => (
                <li
                  key={op.userId}
                  className="flex items-center justify-between gap-2 text-sm"
                  data-testid={`labor-row-${op.userId}`}
                >
                  <span className="truncate font-medium text-text">
                    <span className="mr-2 font-mono text-text-muted">{idx + 1}.</span>
                    {op.operatorName}
                  </span>
                  <span className="shrink-0 font-mono tabular-nums text-text">
                    {formatPph(op.activePph)} PPH
                  </span>
                </li>
              ))}
            </ol>
          )}
          {summaryRows.me && !summaryRows.meInTop && (
            <p
              className="mt-3 border-t border-border pt-2 text-xs text-text-muted"
              data-testid="labor-velocity-you"
            >
              You · rank #{summaryRows.meRank} · {formatPph(summaryRows.me.activePph)} PPH
            </p>
          )}
          {summaryRows.me && summaryRows.meInTop && (
            <p className="mt-2 text-xs text-text-muted" data-testid="labor-velocity-you">
              You are in the top 3
            </p>
          )}
        </div>

        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="mt-2 w-full justify-center"
          data-testid="labor-velocity-view-full"
          onClick={() => navigate('/reports?tab=labor')}
        >
          View Full Leaderboard
        </Button>
      </section>
    );
  }

  return (
    <section
      className="rounded-2xl bg-surface-raised p-5 shadow-card"
      data-testid="labor-velocity-leaderboard"
      data-mode="full"
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

      {isLoading && (
        <div data-testid="list-page-loading">
          <TableSkeleton rows={4} cols={5} />
        </div>
      )}
      {isError && (
        <p className="text-sm text-text-muted" data-testid="list-page-error">
          Labor velocity unavailable for this session.{' '}
          <button type="button" className="text-accent hover:underline" onClick={() => void refetch()}>
            Retry
          </button>
        </p>
      )}
      {!isLoading && !isError && operators.length === 0 && (
        <p className="text-sm text-text-muted" data-testid="list-page-empty">
          No floor pick / ship activity today yet. Claim a wave and pick to populate the board.
        </p>
      )}
      {!isLoading && operators.length > 0 && <FullLeaderboardTable operators={operators} />}
    </section>
  );
}
