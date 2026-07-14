import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { CheckCircle, RotateCcw, ScanLine, XCircle } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Return, ReturnLine } from '@/api/types';
import { useBarcodeScanner } from '@/hooks/useBarcodeScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { BigButton } from '@/components/ui/BigButton';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { Card } from '@/components/ui/Card';

interface ReceiveState {
  returnData: Return;
  confirmedLines: Set<string>;
}

export function ReturnsReceivePage() {
  const lastScan = useScanBufferStore((s) => s.lastScan);
  const { flash, triggerSuccess, triggerError } = useScanFeedback();
  const [state, setState] = useState<ReceiveState | null>(null);

  const lookupMutation = useMutation({
    mutationFn: async (barcode: string) => {
      const res = await apiClient.get<Return>(`/api/v1/returns/by-barcode/${encodeURIComponent(barcode)}`);
      return res.data;
    },
    onSuccess: (returnData) => {
      triggerSuccess();
      setState({ returnData, confirmedLines: new Set() });
    },
    onError: () => triggerError(),
  });

  const confirmMutation = useMutation({
    mutationFn: async ({ returnId, lineId }: { returnId: string; lineId: string }) => {
      await apiClient.post(`/api/v1/returns/${returnId}/lines/${lineId}/receive`, {
        quantity: 1,
      });
    },
    onSuccess: (_data, { lineId }) => {
      triggerSuccess();
      setState((prev) => {
        if (!prev) return prev;
        const confirmedLines = new Set(prev.confirmedLines);
        confirmedLines.add(lineId);
        return { ...prev, confirmedLines };
      });
    },
    onError: () => triggerError(),
  });

  useBarcodeScanner({
    enabled: true,
    captureAll: true,
    onScan: (barcode) => {
      if (barcode.length === 0) return;
      if (!state) {
        lookupMutation.mutate(barcode);
      }
    },
  });

  const lines = state?.returnData.lines ?? [];

  return (
    <div className="flex min-h-full flex-col p-4 pb-8" data-theme="warehouse">
      <ScanFlashOverlay flash={flash} />

      <div className="mb-6 text-center">
        <div className="mb-2 flex items-center justify-center gap-2">
          <RotateCcw className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Returns Receive</h1>
        </div>
        <p className="text-sm text-text-muted">Scan RMA barcode to begin receiving</p>
      </div>

      <Card className="mb-6 text-center" padding="lg">
        <ScanLine className="mx-auto mb-3 h-10 w-10 text-accent" />
        <p className="text-sm text-text-muted">Last scan</p>
        <p className="mt-1 font-mono text-2xl font-bold text-text">
          {lastScan ?? 'Scan RMA barcode'}
        </p>
        {lookupMutation.isPending && (
          <p className="mt-2 text-sm text-accent">Looking up RMA...</p>
        )}
      </Card>

      {state && (
        <>
          <Card className="mb-4" padding="md">
            <p className="font-mono text-lg font-bold text-text">{state.returnData.number}</p>
            <p className="text-sm text-text-muted">
              Order {state.returnData.salesOrderNumber ?? state.returnData.salesOrderId}
            </p>
          </Card>

          <div className="mb-6 space-y-3">
            {lines.map((line) => (
              <LineConfirmCard
                key={line.id}
                line={line}
                confirmed={state.confirmedLines.has(line.id)}
                onConfirm={() =>
                  confirmMutation.mutate({
                    returnId: state.returnData.id,
                    lineId: line.id,
                  })
                }
                loading={confirmMutation.isPending}
              />
            ))}
          </div>

          <BigButton variant="secondary" onClick={() => setState(null)}>
            Scan next RMA
          </BigButton>
        </>
      )}
    </div>
  );
}

function LineConfirmCard({
  line,
  confirmed,
  onConfirm,
  loading,
}: {
  line: ReturnLine;
  confirmed: boolean;
  onConfirm: () => void;
  loading: boolean;
}) {
  const [photoAttached, setPhotoAttached] = useState(false);

  return (
    <Card padding="md" className={confirmed ? 'border-success/40' : undefined}>
      <div className="flex items-start gap-3">
        {confirmed ? (
          <CheckCircle className="mt-0.5 h-5 w-5 shrink-0 text-success" />
        ) : (
          <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-text-muted" />
        )}
        <div className="min-w-0 flex-1">
          <p className="font-mono font-medium text-text">{line.sku ?? line.productName ?? line.id}</p>
          <p className="text-sm text-text-muted">
            Expected {line.quantityExpected} · Received {line.quantityReceived}
          </p>
          {line.putawayTarget && (
            <p className="mt-1 text-sm font-medium text-accent">
              Putaway target: {line.putawayTarget.replace(/\//g, ' / ')}
            </p>
          )}
          {!confirmed && line.quantityReceived < line.quantityExpected && (
            <div className="mt-3 space-y-3" data-testid="return-condition-photo">
              <MediaPicker
                kind="EVIDENCE"
                label="Condition photo"
                capture
                webrtc
                presignType="TRANSACTION"
                onUploaded={async (result) => {
                  await apiClient.post('/api/v1/media/transactions', {
                    entityType: 'RETURN_LINE',
                    entityId: line.id,
                    url: result.contentUrl,
                  });
                  await apiClient.post('/api/v1/media/attachments', {
                    mediaObjectId: result.id,
                    entityType: 'RETURN_LINE',
                    entityId: line.id,
                    purpose: 'RETURN_CONDITION',
                  });
                  setPhotoAttached(true);
                }}
              />
              {photoAttached && (
                <p className="text-xs text-success">Condition photo saved</p>
              )}
              <BigButton variant="success" loading={loading} onClick={onConfirm} className="w-full">
                Confirm +1
              </BigButton>
            </div>
          )}
          {confirmed && <p className="mt-2 text-sm text-success">Received</p>}
        </div>
      </div>
    </Card>
  );
}
