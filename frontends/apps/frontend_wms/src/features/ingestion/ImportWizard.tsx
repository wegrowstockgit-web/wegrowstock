import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertTriangle, Download, FileUp, Upload } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { cn } from '@/lib/utils';

/** Canonical import targets — headers in the Download Template match these keys. */
export const TARGET_FIELDS = [
  { key: 'sku', label: 'SKU' },
  { key: 'name', label: 'Product name' },
  { key: 'barcode', label: 'Barcode' },
  { key: 'qty', label: 'Quantity' },
  { key: 'unitCost', label: 'Unit cost' },
  { key: 'locationPath', label: 'Location path' },
  { key: 'length', label: 'Length' },
  { key: 'width', label: 'Width' },
  { key: 'height', label: 'Height' },
  { key: 'weight', label: 'Weight' },
  { key: 'dimUnit', label: 'Dim unit' },
  { key: 'weightUnit', label: 'Weight unit' },
  { key: 'hsCode', label: 'HS code' },
  { key: 'countryOfOrigin', label: 'Country of origin' },
  { key: 'lotNumber', label: 'Lot #' },
  { key: 'expiry', label: 'Expiry' },
  { key: 'palletTie', label: 'Pallet Ti' },
  { key: 'palletHigh', label: 'Pallet Hi' },
  { key: 'tempZone', label: 'Temp zone' },
  { key: 'hazmat', label: 'Hazmat' },
  { key: 'fragile', label: 'Fragile' },
  { key: 'abcClassification', label: 'ABC class' },
  { key: 'lifecycleStatus', label: 'Lifecycle' },
  { key: 'uom', label: 'UOM' },
] as const;

type TargetField = (typeof TARGET_FIELDS)[number]['key'];

const EMPTY_MAPPING: Record<TargetField, string> = Object.fromEntries(
  TARGET_FIELDS.map((f) => [f.key, '']),
) as Record<TargetField, string>;

const TEMPLATE_HEADERS = TARGET_FIELDS.map((f) => f.key).join(',');
const TEMPLATE_SAMPLE_ROW = [
  'WIDGET-S',
  'Standard Widget',
  '8901000000001',
  '100',
  '5.00',
  'WH-01/Z-A/B-01',
  '10',
  '8',
  '6',
  '0.5',
  'cm',
  'kg',
  '8471.30',
  'US',
  'LOT-2026-01',
  '2027-12-31',
  '10',
  '4',
  'AMBIENT',
  'false',
  'false',
  'C',
  'ACTIVE',
  'EA',
].join(',');

export function buildImportTemplateCsv(): string {
  return `${TEMPLATE_HEADERS}\n${TEMPLATE_SAMPLE_ROW}\n`;
}

function downloadImportTemplate() {
  const blob = new Blob([buildImportTemplateCsv()], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'invsys-import-template.csv';
  anchor.click();
  URL.revokeObjectURL(url);
}

type ImportResponse = {
  imported: number;
  skipped: number;
  errors: string[];
  fileChecksumSha256?: string;
};

type ImportRowStatus =
  | 'READY_TO_IMPORT'
  | 'MISSING_PRODUCT'
  | 'MISSING_LOCATION'
  | 'MISSING_UOM'
  | 'VALIDATION_ERROR';

type PreflightRow = {
  rowNumber: number;
  sku: string | null;
  name: string | null;
  locationPath: string | null;
  status: ImportRowStatus;
  detail: string | null;
  matchedVariantId: string | null;
  matchedLocationId: string | null;
};

type PreflightResponse = {
  rows: PreflightRow[];
  statusCounts: Partial<Record<ImportRowStatus, number>>;
  missingSkus: string[];
  missingLocationPaths: string[];
  fileChecksumSha256: string;
};

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
    locationPath: pick('locationpath', 'location_path', 'location path', 'path'),
    // Prefer exact enterprise headers — avoid single-letter aliases that collide with other names.
    length: pick('length', 'len', 'dim_length'),
    width: pick('width', 'dim_width'),
    height: pick('height', 'ht', 'dim_height'),
    weight: pick('weight', 'wt', 'dim_weight'),
    dimUnit: pick('dimunit', 'dim_unit', 'dimension unit'),
    weightUnit: pick('weightunit', 'weight_unit'),
    hsCode: pick('hscode', 'hs_code', 'hs code', 'hs_tariff_code', 'tariff'),
    countryOfOrigin: pick('countryoforigin', 'country_of_origin', 'origin', 'coo'),
    lotNumber: pick('lotnumber', 'lot_number', 'lot #', 'lot', 'lotno'),
    expiry: pick('expiry', 'expires', 'expires_at', 'expiration', 'expiry date'),
    palletTie: pick('pallettie', 'pallet_tie', 'ti', 'pallet ti'),
    palletHigh: pick('pallethigh', 'pallet_high', 'hi', 'pallet hi'),
    tempZone: pick('tempzone', 'temp_zone', 'storage_temp_zone', 'temp zone', 'zone'),
    hazmat: pick('hazmat', 'is_hazmat'),
    fragile: pick('fragile', 'is_fragile'),
    abcClassification: pick('abcclassification', 'abc_classification', 'abc'),
    lifecycleStatus: pick('lifecyclestatus', 'lifecycle_status', 'lifecycle'),
    uom: pick('uom', 'unit', 'unit of measure'),
  };
}

type WizardMode = 'import' | 'legacy-migration';

type MigrationResponse = {
  imported: number;
  errors: string[];
};

interface ImportWizardProps {
  defaultMode?: WizardMode;
}

function statusRowClass(status: ImportRowStatus): string {
  switch (status) {
    case 'READY_TO_IMPORT':
      return 'bg-emerald-500/5';
    case 'MISSING_PRODUCT':
    case 'MISSING_LOCATION':
    case 'MISSING_UOM':
      return 'bg-amber-500/15 text-amber-950 dark:text-amber-100';
    case 'VALIDATION_ERROR':
      return 'bg-red-500/10 text-red-900 dark:text-red-100';
    default:
      return '';
  }
}

export function ImportWizard({ defaultMode = 'import' }: ImportWizardProps) {
  const [mode, setMode] = useState<WizardMode>(defaultMode);
  const [file, setFile] = useState<File | null>(null);
  const [previewText, setPreviewText] = useState('');
  const [mapping, setMapping] = useState<Record<TargetField, string>>({ ...EMPTY_MAPPING });
  const [locationId, setLocationId] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const [result, setResult] = useState<(ImportResponse & { skipped?: number }) | null>(null);
  const [preflight, setPreflight] = useState<PreflightResponse | null>(null);
  const [mapToExisting, setMapToExisting] = useState(false);

  const { data: locations = [] } = useQuery({
    queryKey: ['locations', 'WAREHOUSE'],
    queryFn: async () =>
      (await apiClient.get<TenantLocation[]>('/api/v1/locations', { params: { type: 'WAREHOUSE' } }))
        .data,
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
    setPreflight(null);
    if (!next) {
      setPreviewText('');
      return;
    }
    const text = await readFileText(next);
    setPreviewText(text);
    const { headers } = parseCsvPreview(text);
    setMapping(guessMapping(headers));
  }

  function buildFormData(extra?: Record<string, string>): FormData {
    if (!file) throw new Error('Choose a file first');
    const form = new FormData();
    form.append('file', file);
    form.append('columnsMapping', JSON.stringify(mapping));
    if (locationId) form.append('locationId', locationId);
    if (extra) {
      for (const [k, v] of Object.entries(extra)) {
        form.append(k, v);
      }
    }
    return form;
  }

  const preflightMutation = useMutation({
    mutationFn: async () => {
      const form = buildFormData();
      return (await apiClient.post<PreflightResponse>('/api/v1/ingestion/preflight', form)).data;
    },
    onSuccess: (data) => setPreflight(data),
  });

  const createMissingMutation = useMutation({
    mutationFn: async () => {
      const form = buildFormData();
      return (
        await apiClient.post<{ created: number; skipped: number; errors: string[] }>(
          '/api/v1/ingestion/create-missing-products',
          form,
        )
      ).data;
    },
    onSuccess: async () => {
      await preflightMutation.mutateAsync();
    },
  });

  const importMutation = useMutation({
    mutationFn: async () => {
      if (mode === 'legacy-migration') {
        const form = buildFormData();
        const data = (
          await apiClient.post<MigrationResponse>('/api/v1/ingestion/legacy-migration', form)
        ).data;
        return { imported: data.imported, skipped: 0, errors: data.errors };
      }
      const form = buildFormData({
        createMissingProducts: 'false',
        createMissingLocations: mapToExisting ? 'true' : 'false',
      });
      return (await apiClient.post<ImportResponse>('/api/v1/ingestion/import', form)).data;
    },
    onSuccess: (data) => setResult(data),
  });

  useEffect(() => {
    if (mode !== 'import' || !file || !mapping.sku) return;
    const handle = window.setTimeout(() => {
      preflightMutation.mutate();
    }, 350);
    return () => window.clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-run when mapping/location/file change
  }, [mode, file, locationId, mapping]);

  const readyCount = preflight?.statusCounts?.READY_TO_IMPORT ?? 0;
  const missingProductCount = preflight?.statusCounts?.MISSING_PRODUCT ?? 0;
  const warningCount =
    (preflight?.statusCounts?.MISSING_PRODUCT ?? 0) +
    (preflight?.statusCounts?.MISSING_LOCATION ?? 0) +
    (preflight?.statusCounts?.MISSING_UOM ?? 0) +
    (preflight?.statusCounts?.VALIDATION_ERROR ?? 0);

  return (
    <div className="space-y-6" data-testid="import-wizard">
      <div>
        <h1 className="text-2xl font-bold text-text">
          {mode === 'legacy-migration' ? 'Legacy ERP migration' : 'Data import'}
        </h1>
        <p className="mt-1 text-sm text-text-muted">
          {mode === 'legacy-migration'
            ? 'One-click cutover: bulk products/variants plus INITIAL_MIGRATION receives in a single transaction.'
            : 'Cold-start safe: pre-flight resolves missing SKUs and locations before any ledger write.'}
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant={mode === 'import' ? 'primary' : 'secondary'}
            onClick={() => setMode('import')}
          >
            CSV import
          </Button>
          <Button
            type="button"
            size="sm"
            variant={mode === 'legacy-migration' ? 'primary' : 'secondary'}
            data-testid="legacy-migration-mode"
            onClick={() => setMode('legacy-migration')}
          >
            Legacy ERP migration
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader
          title="1. Upload file"
          description="CSV with a header row (Excel export works)"
          action={
            <Button
              type="button"
              size="sm"
              variant="secondary"
              data-testid="download-import-template"
              onClick={downloadImportTemplate}
            >
              <Download className="h-4 w-4" />
              Download Template
            </Button>
          }
        />
        <p className="mb-3 text-xs text-text-muted">
          Enterprise template includes location path, dimensions, HS code, lot/expiry, pallet Ti-Hi,
          trade/handling, and lifecycle fields.
        </p>
        <div
          className={cn(
            'flex min-h-40 cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border px-4 py-8 text-center transition-colors',
            dragOver ? 'border-accent bg-accent/5' : 'bg-surface-overlay/40',
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
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
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
            label="Fallback warehouse (when location_path omitted)"
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
        <CardHeader title="3. Mapped preview" description="First rows after mapping" />
        {preview.headers.length === 0 ? (
          <p className="text-sm text-text-muted">Upload a file to preview mapped values.</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  {TARGET_FIELDS.slice(0, 8).map((f) => (
                    <TableHead key={f.key}>{f.label}</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {preview.rows.map((row, idx) => (
                  <TableRow key={idx}>
                    {TARGET_FIELDS.slice(0, 8).map((f) => {
                      const header = mapping[f.key];
                      const colIdx = preview.headers.findIndex(
                        (h) => h.toLowerCase() === header.toLowerCase(),
                      );
                      return (
                        <TableCell key={f.key}>
                          {colIdx >= 0 ? row[colIdx] ?? '' : '—'}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      {mode === 'import' && (
        <Card data-testid="validation-preview">
          <CardHeader
            title="4. Validation preview"
            description="Pre-flight resolution map — missing references are never committed"
          />
          {!file || !mapping.sku ? (
            <p className="text-sm text-text-muted">Upload a file and map SKU to run pre-flight.</p>
          ) : preflightMutation.isPending && !preflight ? (
            <p className="text-sm text-text-muted">Running pre-flight validation…</p>
          ) : preflightMutation.isError ? (
            <p className="text-sm text-danger">Pre-flight failed. Check mapping and file format.</p>
          ) : preflight ? (
            <>
              <div className="mb-4 flex flex-wrap gap-3 text-sm">
                <span className="rounded bg-emerald-500/15 px-2 py-1">
                  Ready: {readyCount}
                </span>
                <span className="rounded bg-amber-500/15 px-2 py-1">
                  Needs resolution: {warningCount}
                </span>
                {preflight.fileChecksumSha256 && (
                  <span className="font-mono text-xs text-text-muted">
                    checksum {preflight.fileChecksumSha256.slice(0, 12)}…
                  </span>
                )}
              </div>

              {(missingProductCount > 0 || (preflight.missingLocationPaths?.length ?? 0) > 0) && (
                <div
                  className="mb-4 space-y-3 rounded-lg border border-amber-500/40 bg-amber-500/10 p-4"
                  data-testid="bulk-actions"
                >
                  <div className="flex items-start gap-2 text-sm">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
                    <div>
                      <p className="font-medium">Bulk actions</p>
                      <p className="text-text-muted">
                        Resolve missing catalog or location references before importing ready rows.
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      data-testid="create-missing-products"
                      loading={createMissingMutation.isPending}
                      disabled={missingProductCount === 0}
                      onClick={() => createMissingMutation.mutate()}
                    >
                      Create missing products based on CSV data
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant={mapToExisting ? 'primary' : 'secondary'}
                      data-testid="map-to-existing"
                      onClick={() => setMapToExisting((v) => !v)}
                    >
                      Map to existing
                      {mapToExisting ? ' (on)' : ''}
                    </Button>
                  </div>
                  {mapToExisting && (
                    <p className="text-xs text-text-muted">
                      When on, import will create missing location paths from CSV and use the
                      fallback warehouse for rows without a path. Products still require create or
                      an existing SKU match.
                    </p>
                  )}
                  {createMissingMutation.isError && (
                    <p className="text-sm text-danger">
                      Could not create missing products. Ensure length/width/height are present.
                    </p>
                  )}
                </div>
              )}

              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Row</TableHead>
                      <TableHead>SKU</TableHead>
                      <TableHead>Name</TableHead>
                      <TableHead>Location path</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Detail</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {preflight.rows.map((row) => (
                      <TableRow
                        key={row.rowNumber}
                        className={statusRowClass(row.status)}
                        data-testid={`preflight-row-${row.rowNumber}`}
                        data-status={row.status}
                      >
                        <TableCell>{row.rowNumber}</TableCell>
                        <TableCell>{row.sku ?? '—'}</TableCell>
                        <TableCell>{row.name ?? '—'}</TableCell>
                        <TableCell className="font-mono text-xs">
                          {row.locationPath ?? '—'}
                        </TableCell>
                        <TableCell className="font-medium">{row.status}</TableCell>
                        <TableCell className="text-sm">{row.detail ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </>
          ) : (
            <p className="text-sm text-text-muted">Waiting for pre-flight…</p>
          )}
        </Card>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <Button
          onClick={() => importMutation.mutate()}
          loading={importMutation.isPending}
          disabled={
            !file ||
            !mapping.sku ||
            (mode === 'import' && readyCount === 0 && !importMutation.isPending)
          }
          data-testid="import-submit"
        >
          <Upload className="h-4 w-4" />
          {mode === 'legacy-migration'
            ? 'Run migration'
            : readyCount > 0
              ? `Import ${readyCount} ready row${readyCount === 1 ? '' : 's'}`
              : 'Run import'}
        </Button>
        {mode === 'import' && (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            loading={preflightMutation.isPending}
            disabled={!file || !mapping.sku}
            onClick={() => preflightMutation.mutate()}
            data-testid="rerun-preflight"
          >
            Re-run pre-flight
          </Button>
        )}
        {importMutation.isError && (
          <span className="text-sm text-danger">Import failed. Check mapping and file format.</span>
        )}
      </div>

      {result && (
        <Card>
          <CardHeader
            title={mode === 'legacy-migration' ? 'Migration result' : 'Import result'}
            description={
              mode === 'legacy-migration'
                ? 'Atomic INITIAL_MIGRATION ledger receives'
                : 'Append-only ledger receives for READY_TO_IMPORT rows only'
            }
          />
          <p className="text-sm text-text">
            Imported {result.imported}
            {typeof result.skipped === 'number' ? `, skipped ${result.skipped}` : ''}
          </p>
          {result.fileChecksumSha256 && (
            <p className="mt-1 font-mono text-xs text-text-muted">
              Audit checksum: {result.fileChecksumSha256}
            </p>
          )}
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
