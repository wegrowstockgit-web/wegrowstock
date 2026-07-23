import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useThermalPrinter } from './useThermalPrinter';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe('useThermalPrinter', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it('lists printers and posts to default print endpoint', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{ id: 'p1', name: 'Dock Zebra', printerType: 'DIRECT_SOCKET', isDefault: true }],
    } as never);
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);

    const { result } = renderHook(() => useThermalPrinter(), { wrapper });

    await waitFor(() => {
      expect(result.current.printers).toHaveLength(1);
    });

    await result.current.printLabel('^XA^FDTest^FS^XZ');

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/thermal-printers/print-default', {
      zpl: '^XA^FDTest^FS^XZ',
    });
  });

  it('posts to a specific printer when printerId is provided', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] } as never);
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never);

    const { result } = renderHook(() => useThermalPrinter(), { wrapper });

    await waitFor(() => {
      expect(result.current.isLoadingPrinters).toBe(false);
    });

    await result.current.printLabel('^XA^XZ', 'printer-42');

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/thermal-printers/printer-42/print', {
      zpl: '^XA^XZ',
    });
  });
});
