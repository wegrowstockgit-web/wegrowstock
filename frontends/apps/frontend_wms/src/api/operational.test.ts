import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listCustomers, listInvoices, listManufacturingOrders, listProducts, listPurchaseOrders, listSalesOrders, listSuppliers } from './operational';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

describe('operational list clients', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('maps page/size/search/sort onto purchase-order queries', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { items: [{ id: 'po-1', number: 'PO-1' }], totalElements: 1, totalPages: 1, page: 1, size: 25 },
    });
    const page = await listPurchaseOrders({ page: 2, size: 25, search: 'Acme', sort: 'number,asc' });
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/purchase-orders', {
      params: { page: 2, size: 25, sort: 'number,asc', search: 'Acme' },
    });
    expect(page.items[0].number).toBe('PO-1');
    expect(page.totalElements).toBe(1);
  });

  it('accepts a legacy array envelope for lookups', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: [{ id: 's1', name: 'Acme' }] });
    const page = await listSuppliers({ page: 1, size: 50 });
    expect(page.items).toEqual([{ id: 's1', name: 'Acme' }]);
    expect(page.totalElements).toBe(1);
  });

  it('lists remaining operational tables with optional status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { items: [], totalElements: 0, totalPages: 0, page: 1, size: 50 } });
    await listSalesOrders({ page: 1, size: 50, status: 'DRAFT' });
    await listCustomers({ page: 1 });
    await listInvoices({ page: 1, status: 'OPEN' });
    await listProducts({});
    await listManufacturingOrders({ search: 'MO-1' });
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/sales-orders', {
      params: { page: 1, size: 50, status: 'DRAFT' },
    });
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/manufacturing/orders', {
      params: { page: 1, size: 50, search: 'MO-1' },
    });
  });
});
