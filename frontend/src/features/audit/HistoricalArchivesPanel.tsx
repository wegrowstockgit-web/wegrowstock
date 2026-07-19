import { useState } from 'react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';

function defaultEnd(): string {
  return new Date().toISOString().slice(0, 10);
}

function defaultStart(): string {
  const d = new Date();
  d.setUTCMonth(d.getUTCMonth() - 3);
  return d.toISOString().slice(0, 10);
}

/**
 * OWNER/ADMIN download of gunzipped cold audit JSONL from S3 via authenticated blob fetch.
 */
export function HistoricalArchivesPanel() {
  const [startDate, setStartDate] = useState(() => defaultStart());
  const [endDate, setEndDate] = useState(() => defaultEnd());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function downloadArchive() {
    setError(null);
    if (!startDate || !endDate) {
      setError('Start date and end date are required.');
      return;
    }
    if (startDate > endDate) {
      setError('Start date must be on or before end date.');
      return;
    }

    setBusy(true);
    let objectUrl: string | null = null;
    try {
      const res = await apiClient.get<Blob>('/api/v1/office/audit/archives/download', {
        params: { startDate, endDate },
        responseType: 'blob',
        timeout: 120_000,
      });
      const blob = new Blob([res.data], { type: 'application/x-ndjson' });
      objectUrl = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = 'audit_archive.jsonl';
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } catch {
      setError('Could not download historical archives for this range.');
    } finally {
      if (objectUrl) {
        window.URL.revokeObjectURL(objectUrl);
      }
      setBusy(false);
    }
  }

  return (
    <Card data-testid="historical-archives-panel">
      <CardHeader
        title="Historical Archives"
        description="Download cold-stored audit JSONL (older than hot retention) for compliance review"
      />
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm text-text">
          <span className="text-xs font-medium text-text-muted">Start Date</span>
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="h-10 rounded-md border border-border bg-surface-raised px-3 text-sm text-text"
            data-testid="archive-start-date"
            aria-label="Start Date"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-text">
          <span className="text-xs font-medium text-text-muted">End Date</span>
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="h-10 rounded-md border border-border bg-surface-raised px-3 text-sm text-text"
            data-testid="archive-end-date"
            aria-label="End Date"
          />
        </label>
        <Button
          type="button"
          variant="secondary"
          loading={busy}
          disabled={busy}
          onClick={() => void downloadArchive()}
          data-testid="archive-download-button"
        >
          Download Archive
        </Button>
      </div>
      {error && (
        <p className="mt-3 text-sm text-danger" data-testid="archive-download-error">
          {error}
        </p>
      )}
    </Card>
  );
}
