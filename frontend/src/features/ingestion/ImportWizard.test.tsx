import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ImportWizard } from './ImportWizard';
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

describe('ImportWizard', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ id: 'wh-1', name: 'Main', code: 'WH1', type: 'WAREHOUSE', path: 'WH1' }],
    } as never);
    vi.mocked(apiClient.post).mockReset();
  });

  it('parses CSV preview and posts multipart import', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { imported: 1, skipped: 0, errors: [] },
    } as never);

    renderWizard();
    await screen.findByText(/Data import/i);

    const csv = 'sku,name,qty\nA,Widget,2\n';
    const file = new File([csv], 'stock.csv', { type: 'text/csv' });
    // jsdom File may omit Blob.text(); provide a polyfill for the wizard reader.
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
      expect(screen.getByText('Widget')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Run import/i }));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/ingestion/import',
        expect.any(FormData),
      );
    });
    expect(await screen.findByText(/Imported 1/)).toBeInTheDocument();
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
    vi.mocked(apiClient.post).mockRejectedValue(new Error('boom'));
    renderWizard();
    await screen.findByText(/Data import/i);
    const csv = 'sku,name,qty\nA,Widget,2\n';
    const file = new File([csv], 'fail.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { value: async () => csv });
    const input = document.getElementById('ingestion-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);
    await waitFor(() => expect(screen.getByText('fail.csv')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /Run import/i }));
    expect(await screen.findByText(/Import failed/i)).toBeInTheDocument();
  });

  it('shows skipped/error summary from import response', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: {
        imported: 0,
        skipped: 1,
        errors: ['Row 2: qty must be positive'],
      },
    } as never);

    renderWizard();
    await screen.findByText(/Data import/i);
    const csv = 'sku,name,qty\nBAD,Bad,-1\n';
    const file = new File([csv], 'bad.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', { value: async () => csv });
    const input = document.getElementById('ingestion-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    fireEvent.change(input);
    await waitFor(() => expect(screen.getByText('bad.csv')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /Run import/i }));
    expect(await screen.findByText(/Imported 0, skipped 1/i)).toBeInTheDocument();
    expect(await screen.findByText(/qty must be positive/i)).toBeInTheDocument();
  });
});
