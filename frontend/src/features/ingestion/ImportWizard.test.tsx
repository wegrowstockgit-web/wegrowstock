import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { buildImportTemplateCsv, ImportWizard } from './ImportWizard';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

function renderWizard() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ImportWizard />
    </QueryClientProvider>,
  );
}

const readyPreflight = {
  rows: [
    {
      rowNumber: 2,
      sku: 'A',
      name: 'Widget',
      locationPath: null,
      status: 'READY_TO_IMPORT',
      detail: 'Matched existing SKU',
      matchedVariantId: 'v1',
      matchedLocationId: 'wh-1',
    },
  ],
  statusCounts: { READY_TO_IMPORT: 1 },
  missingSkus: [],
  missingLocationPaths: [],
  fileChecksumSha256: 'abc123checksum',
};

const missingPreflight = {
  rows: [
    {
      rowNumber: 2,
      sku: 'NEW-1',
      name: 'New',
      locationPath: 'WH-01/Z-A',
      status: 'MISSING_PRODUCT',
      detail: 'SKU not found in catalog',
      matchedVariantId: null,
      matchedLocationId: null,
    },
  ],
  statusCounts: { MISSING_PRODUCT: 1 },
  missingSkus: ['NEW-1'],
  missingLocationPaths: ['WH-01/Z-A'],
  fileChecksumSha256: 'def456',
};

describe('ImportWizard', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ id: 'wh-1', name: 'Main', code: 'WH1', type: 'WAREHOUSE', path: 'WH1' }],
    } as never);
    vi.mocked(apiClient.post).mockReset();
    vi.mocked(apiClient.post).mockImplementation(async (url: string) => {
      if (url.includes('/preflight')) {
        return { data: readyPreflight } as never;
      }
      if (url.includes('/import')) {
        return { data: { imported: 1, skipped: 0, errors: [], fileChecksumSha256: 'abc123checksum' } } as never;
      }
      if (url.includes('/create-missing-products')) {
        return { data: { created: 1, skipped: 0, errors: [] } } as never;
      }
      return { data: {} } as never;
    });
  });

  it('exposes enterprise template headers via Download Template', () => {
    const csv = buildImportTemplateCsv();
    expect(csv).toContain('hsCode');
    expect(csv).toContain('locationPath');
    expect(csv).toContain('length');
    expect(csv).toContain('width');
    expect(csv).toContain('height');
    expect(csv).toContain('palletTie');
    expect(csv).toContain('lifecycleStatus');
    expect(csv.split('\n')[0]).toContain('locationPath');
    expect(csv.split('\n')[0]).toContain('hsCode');
  });

  it('renders Download Template and enterprise mapping dropdowns', async () => {
    renderWizard();
    await screen.findByText(/Data import/i);
    expect(screen.getByTestId('download-import-template')).toBeInTheDocument();
    expect(screen.getByLabelText('HS code')).toBeInTheDocument();
    expect(screen.getByLabelText('Location path')).toBeInTheDocument();
    expect(screen.getByLabelText('Length')).toBeInTheDocument();
    expect(screen.getByLabelText('Lifecycle')).toBeInTheDocument();
  });

  it('runs preflight after file load and posts import for ready rows', async () => {
    renderWizard();
    await screen.findByText(/Data import/i);

    const csv = 'sku,name,qty\nA,Widget,2\n';
    const file = new File([csv], 'stock.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: async () => csv,
    });
    const input = document.getElementById('ingestion-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);

    await waitFor(() => {
      expect(screen.getByText('stock.csv')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/ingestion/preflight',
        expect.any(FormData),
      );
    });
    expect(await screen.findByTestId('validation-preview')).toBeInTheDocument();
    expect(await screen.findByText('READY_TO_IMPORT')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('import-submit'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/ingestion/import',
        expect.any(FormData),
      );
    });
    expect(await screen.findByText(/Imported 1/)).toBeInTheDocument();
  });

  it('shows bulk actions for missing products', async () => {
    vi.mocked(apiClient.post).mockImplementation(async (url: string) => {
      if (url.includes('/preflight')) {
        return { data: missingPreflight } as never;
      }
      if (url.includes('/create-missing-products')) {
        return { data: { created: 1, skipped: 0, errors: [] } } as never;
      }
      return { data: readyPreflight } as never;
    });

    renderWizard();
    await screen.findByText(/Data import/i);
    const csv = 'sku,name,qty,length,width,height\nNEW-1,New,1,10,8,6\n';
    const file = new File([csv], 'cold.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { value: async () => csv });
    const input = document.getElementById('ingestion-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);

    expect(await screen.findByTestId('bulk-actions')).toBeInTheDocument();
    expect(screen.getByTestId('create-missing-products')).toBeInTheDocument();
    expect(screen.getByTestId('map-to-existing')).toBeInTheDocument();
    expect(screen.getByText('MISSING_PRODUCT')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('create-missing-products'));
    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/ingestion/create-missing-products',
        expect.any(FormData),
      );
    });
  });

  it('supports drag-and-drop file load', async () => {
    renderWizard();
    await screen.findByText(/Data import/i);
    const csv = 'sku,name,qty\nB,DropItem,3\n';
    const file = new File([csv], 'drop.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { value: async () => csv });

    const dropZone = screen.getByText(/Drop a CSV here/i).closest('div')!;
    fireEvent.dragOver(dropZone);
    fireEvent.drop(dropZone, {
      dataTransfer: { files: [file] },
    });

    await waitFor(() => {
      expect(screen.getByText('drop.csv')).toBeInTheDocument();
      expect(screen.getByText('DropItem')).toBeInTheDocument();
    });
  });

  it('reads CSV via FileReader when Blob.text is unavailable', async () => {
    renderWizard();
    await screen.findByText(/Data import/i);
    const csv = 'sku,name,qty\nC,ReaderItem,1\n';
    const file = new File([csv], 'reader.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { configurable: true, value: undefined });

    const input = document.getElementById('ingestion-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);

    await waitFor(() => {
      expect(screen.getByText('reader.csv')).toBeInTheDocument();
      expect(screen.getByText('ReaderItem')).toBeInTheDocument();
    });
  });

  it('surfaces import mutation failure', async () => {
    vi.mocked(apiClient.post).mockImplementation(async (url: string) => {
      if (url.includes('/preflight')) {
        return { data: readyPreflight } as never;
      }
      throw new Error('boom');
    });
    renderWizard();
    await screen.findByText(/Data import/i);
    const csv = 'sku,name,qty\nA,Widget,2\n';
    const file = new File([csv], 'fail.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { value: async () => csv });
    const input = document.getElementById('ingestion-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);
    await waitFor(() => expect(screen.getByText('READY_TO_IMPORT')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('import-submit'));
    expect(await screen.findByText(/Import failed/i)).toBeInTheDocument();
  });
});
