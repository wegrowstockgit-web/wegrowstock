import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { formatSortParam, parseSortParam, useServerTableQuery } from './useServerTable';
import { apiClient } from '@/api/client';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

function Probe() {
  const table = useServerTableQuery<{ id: string; number: string }>({
    queryKey: ['purchase-orders'],
    path: '/api/v1/purchase-orders',
    extraParams: { status: 'OPEN' },
    fetcher: async (query) => {
      const { data } = await apiClient.get('/api/v1/purchase-orders', {
        params: { page: query.page, size: query.size, search: query.search, sort: query.sort, status: query.status },
      });
      return data;
    },
  });
  return (
    <div>
      <span data-testid="count">{table.items.length}</span>
      <span data-testid="total">{table.totalElements}</span>
      <button type="button" onClick={() => table.setPage(2)}>
        next
      </button>
      <button type="button" onClick={() => table.setSearch('Acme')}>
        search
      </button>
      <button type="button" onClick={() => table.setSize(25)}>
        size
      </button>
      <button type="button" onClick={() => table.toggleSort('number')}>
        sort
      </button>
    </div>
  );
}

describe('useServerTableQuery', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        items: [{ id: '1', number: 'PO-1' }],
        totalElements: 3,
        totalPages: 2,
        page: 1,
        size: 50,
        hasMore: true,
      },
    });
  });

  it('loads a page envelope and puts page/search in the query key via the URL', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/purchase-orders']}>
          <Probe />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('1'));
    expect(screen.getByTestId('total')).toHaveTextContent('3');
    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/purchase-orders',
      expect.objectContaining({
        params: expect.objectContaining({ page: 1, size: 50, sort: 'createdAt,desc', status: 'OPEN' }),
      }),
    );
    fireEvent.click(screen.getByText('next'));
    await waitFor(() =>
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/purchase-orders',
        expect.objectContaining({ params: expect.objectContaining({ page: 2 }) }),
      ),
    );
    fireEvent.click(screen.getByText('search'));
    await waitFor(() =>
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/purchase-orders',
        expect.objectContaining({ params: expect.objectContaining({ search: 'Acme' }) }),
      ),
    );
    fireEvent.click(screen.getByText('size'));
    await waitFor(() =>
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/purchase-orders',
        expect.objectContaining({ params: expect.objectContaining({ size: 25 }) }),
      ),
    );
    fireEvent.click(screen.getByText('sort'));
    await waitFor(() =>
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/purchase-orders',
        expect.objectContaining({ params: expect.objectContaining({ sort: 'number,asc' }) }),
      ),
    );
  });

  it('uses the default GET client when no fetcher is provided', async () => {
    function DefaultProbe() {
      const table = useServerTableQuery<{ id: string }>({
        queryKey: 'suppliers',
        path: '/api/v1/suppliers',
        defaultSort: 'name,asc',
      });
      return <span data-testid="count">{table.items.length}</span>;
    }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/suppliers']}>
          <DefaultProbe />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('1'));
    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/suppliers',
      expect.objectContaining({
        params: expect.objectContaining({ page: 1, size: 50, sort: 'name,asc' }),
      }),
    );
  });

  it('parses and formats sort params', () => {
    expect(parseSortParam('number,asc', 'createdAt,desc')).toEqual({ key: 'number', dir: 'asc' });
    expect(parseSortParam('', 'createdAt,desc')).toEqual({ key: 'createdAt', dir: 'desc' });
    expect(formatSortParam('name', 'desc')).toBe('name,desc');
  });
});
