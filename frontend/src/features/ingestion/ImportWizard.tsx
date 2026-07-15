import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { FileUp, Upload } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { cn } from '@/lib/utils';

const TARGET_FIELDS = [
  { key: 'sku', label: 'SKU' },
  { key: 'name', label: 'Product name' },
  { key: 'barcode', label: 'Barcode' },
  { key: 'qty', label: 'Quantity' },
  { key: 'unitCost', label: 'Unit cost' },
] as const;

type TargetField = (typeof TARGET_FIELDS)[number]['key'];

type ImportResponse = {
  imported: number;
  skipped: number;
  errors: string[];
};

function parseCsvPreview(text: string, maxRows = 8): { headers: string[]; rows: string[][] } {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length === 0) return { headers: [], rows: [] };
  const headers = splitCsvLine(lines[0]).map((h) => h.trim());
  const rows = lines.slice(1, maxRows + 1).map(splitCsvLine);
  return { headers, rows };
}

function splitCsvLine(line: string): string[] {
  const parts: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      inQuotes = !inQuotes;
    } else if (c === ',' && !inQuotes) {
      parts.push(current);
      current = '';
    } else {
      current += c;
    }
  }
  parts.push(current);
  return parts;
}

function guessMapping(headers: string[]): Record<TargetField, string> {
  const lower = headers.map((h) => h.toLowerCase());
  const pick = (...aliases: string[]) => {
    for (const alias of aliases) {
      const idx = lower.indexOf(alias);
      if (idx >= 0) return headers[idx];
    }
    return '';
  };
  return {
    sku: pick('sku', 'item', 'item sku'),
    name: pick('name', 'product', 'title', 'description'),
    barcode: pick('barcode', 'upc', 'ean'),
    qty: pick('qty', 'quantity', 'on hand', 'stock'),
    unitCost: pick('unitcost', 'unit_cost', 'cost', 'unit cost'),
  };
}

export function ImportWizard() {
  const [file, setFile] = useState<File | null>(null);
  const [previewText, setPreviewText] = useState('');
  const [mapping, setMapping] = useState<Record<TargetField, string>>({
    sku: '',
    name: '',
    barcode: '',
    qty: '',
    unitCost: '',
  });
  const [locationId, setLocationId] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const [result, setResult] = useState<ImportResponse | null>(null);

  const { data: locations = [] } = useQuery({
    queryKey: ['locations', 'WAREHOUSE'],
    queryFn: async () =>
      (await apiClient.get<TenantLocation[]>('/api/v1/locations', { params: { type: 'WAREHOUSE' } })).data,
  });

  const preview = useMemo(() => parseCsvPreview(previewText), [previewText]);

  async function readFileText(blob: Blob): Promise<string> {
    if (typeof blob.text === 'function') {
      return blob.text();
    }
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result ?? ''));
      reader.onerror = () => reject(reader.error ?? new Error('Failed to read file'));
      reader.readAsText(blob);
    });
  }

  async function loadFile(next: File | null) {
    setFile(next);
    setResult(null);
    if (!next) {
      setPreviewText('');
      return;
    }
    const text = await readFileText(next);
    setPreviewText(text);
    const { headers } = parseCsvPreview(text);
    setMapping(guessMapping(headers));
  }

  const importMutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Choose a file first');
      const form = new FormData();
      form.append('file', file);
      form.append('columnsMapping', JSON.stringify(mapping));
      if (locationId) form.append('locationId', locationId);
      return (await apiClient.post<ImportResponse>('/api/v1/ingestion/import', form)).data;
    },
    onSuccess: (data) => setResult(data),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-text">Data import</h1>
        <p className="mt-1 text-sm text-text-muted">
          Map spreadsheet columns, preview rows, then stream inventory into the ledger.
        </p>
      </div>

      <Card>
        <CardHeader title="1. Upload file" description="CSV with a header row (Excel export works)" />
        <div
          className={cn(
            'flex min-h-40 cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border px-4 py-8 text-center transition-colors',
            dragOver ? 'border-accent bg-accent/5' : 'bg-surface-overlay/40'
          )}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            void loadFile(e.dataTransfer.files?.[0] ?? null);
          }}
          onClick={() => document.getElementById('ingestion-file-input')?.click()}
        >
          <FileUp className="h-8 w-8 text-text-muted" />
          <div className="text-sm text-text">
            {file ? (
              <span className="font-medium">{file.name}</span>
            ) : (
              <>
                Drop a CSV here, or <span className="text-accent">browse</span>
              </>
            )}
          </div>
          <input
            id="ingestion-file-input"
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(e) => void loadFile(e.target.files?.[0] ?? null)}
          />
        </div>
      </Card>

      <Card>
        <CardHeader title="2. Column mapping" description="Bind source headers to inventory fields" />
        <div className="grid gap-3 sm:grid-cols-2">
          {TARGET_FIELDS.map((field) => (
            <Select
              key={field.key}
              label={field.label}
              value={mapping[field.key]}
              onChange={(e) => setMapping((m) => ({ ...m, [field.key]: e.target.value }))}
              disabled={preview.headers.length === 0}
            >
              <option value="">— skip —</option>
              {preview.headers.map((header) => (
                <option key={header} value={header}>
                  {header}
                </option>
              ))}
            </Select>
          ))}
        </div>
        <div className="mt-4">
          <Select
            label="Receive into warehouse"
            value={locationId}
            onChange={(e) => setLocationId(e.target.value)}
          >
            <option value="">Default warehouse</option>
            {locations.map((loc) => (
              <option key={loc.id} value={loc.id}>
                {loc.name} ({loc.code})
              </option>
            ))}
          </Select>
        </div>
      </Card>

      <Card>
        <CardHeader title="3. Preview" description="First rows after mapping" />
        {preview.headers.length === 0 ? (
          <p className="text-sm text-text-muted">Upload a file to preview mapped values.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead>
                <tr className="border-b border-border text-text-muted">
                  {TARGET_FIELDS.map((f) => (
                    <th key={f.key} className="px-3 py-2 font-medium">
                      {f.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((row, idx) => (
                  <tr key={idx} className="border-b border-border/60">
                    {TARGET_FIELDS.map((f) => {
                      const header = mapping[f.key];
                      const colIdx = preview.headers.findIndex(
                        (h) => h.toLowerCase() === header.toLowerCase()
                      );
                      return (
                        <td key={f.key} className="px-3 py-2 text-text">
                          {colIdx >= 0 ? row[colIdx] ?? '' : '—'}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          onClick={() => importMutation.mutate()}
          loading={importMutation.isPending}
          disabled={!file || !mapping.sku}
        >
          <Upload className="h-4 w-4" />
          Run import
        </Button>
        {importMutation.isError && (
          <span className="text-sm text-danger">Import failed. Check mapping and file format.</span>
        )}
      </div>

      {result && (
        <Card>
          <CardHeader title="Import result" description="Append-only ledger receives for valid rows" />
          <p className="text-sm text-text">
            Imported {result.imported}, skipped {result.skipped}
          </p>
          {result.errors.length > 0 && (
            <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-danger">
              {result.errors.map((err) => (
                <li key={err}>{err}</li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  );
}
