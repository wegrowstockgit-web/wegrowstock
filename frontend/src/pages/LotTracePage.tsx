import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { GitBranch, ScanLine } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { GenealogyNode, LotTraceResponse } from '@/api/types';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';

function TraceTree({ node, depth = 0 }: { node: GenealogyNode; depth?: number }) {
  return (
    <li className="list-none">
      <div
        className="rounded-md border border-border/60 bg-surface-raised px-3 py-2"
        style={{ marginLeft: depth * 16 }}
      >
        <p className="text-sm font-semibold text-text">{node.label}</p>
        <p className="text-xs text-text-muted">
          {node.type}
          {node.detail ? ` · ${node.detail}` : ''}
        </p>
      </div>
      {node.children && node.children.length > 0 && (
        <ul className="mt-2 space-y-2">
          {node.children.map((child) => (
            <TraceTree key={child.id} node={child} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}

export function LotTracePage() {
  const [lotNumber, setLotNumber] = useState('');
  const [trace, setTrace] = useState<LotTraceResponse | null>(null);
  const { triggerSuccess, triggerError } = useScanFeedback();

  const lookupMutation = useMutation({
    mutationFn: async (number: string) => {
      const res = await apiClient.get<LotTraceResponse>('/api/v1/compliance/lots/by-number', {
        params: { lotNumber: number },
      });
      return res.data;
    },
    onSuccess: (data) => {
      triggerSuccess();
      setTrace(data);
    },
    onError: () => {
      triggerError();
      setTrace(null);
    },
  });

  useHardwareScanner({
    enabled: true,
    captureAll: true,
    onScan: (code) => {
      if (!code.length) return;
      setLotNumber(code);
      lookupMutation.mutate(code);
    },
  });

  return (
    <div className="p-6">
      <div className="mb-6">
        <div className="mb-1 flex items-center gap-2">
          <GitBranch className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Lot Trace</h1>
        </div>
        <p className="text-sm text-text-muted">
          Upstream genealogy and downstream recall for a lot number
        </p>
      </div>

      <Card className="mb-6 max-w-xl" padding="md">
        <label className="mb-2 block text-sm font-medium text-text" htmlFor="lot-number">
          Lot number
        </label>
        <div className="flex gap-2">
          <Input
            id="lot-number"
            value={lotNumber}
            onChange={(e) => setLotNumber(e.target.value)}
            placeholder="Scan or type lot number"
            onKeyDown={(e) => {
              if (e.key === 'Enter' && lotNumber.trim()) {
                lookupMutation.mutate(lotNumber.trim());
              }
            }}
          />
          <Button
            disabled={!lotNumber.trim() || lookupMutation.isPending}
            onClick={() => lookupMutation.mutate(lotNumber.trim())}
          >
            <ScanLine className="h-4 w-4" />
            Trace
          </Button>
        </div>
      </Card>

      {trace && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card padding="md">
            <h2 className="mb-3 text-lg font-semibold text-text">Upstream</h2>
            <p className="mb-3 text-sm text-text-muted">
              Lot {trace.lotNumber} · origins and transfers in
            </p>
            <ul className="space-y-2">
              <TraceTree node={trace.upstream} />
            </ul>
          </Card>
          <Card padding="md">
            <h2 className="mb-3 text-lg font-semibold text-text">Downstream</h2>
            <p className="mb-3 text-sm text-text-muted">Shipments and assembly consumption</p>
            <ul className="space-y-2">
              <TraceTree node={trace.downstream} />
            </ul>
          </Card>
        </div>
      )}
    </div>
  );
}
