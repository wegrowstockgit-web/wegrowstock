import { apiClient } from '@/api/client';
import { asPage, type OffsetQuery } from '@/api/page';
import type {
  Customer,
  Invoice,
  PaginatedResponse,
  Product,
  ProductionOrder,
  PurchaseOrder,
  SalesOrder,
  Supplier,
} from '@/api/types';

function listParams(query: OffsetQuery) {
  return {
    page: query.page ?? 1,
    size: query.size ?? 50,
    sort: query.sort,
    ...(query.search ? { search: query.search } : {}),
    ...(query.status ? { status: query.status } : {}),
  };
}

async function getPage<T>(path: string, query: OffsetQuery): Promise<PaginatedResponse<T>> {
  const { data } = await apiClient.get<PaginatedResponse<T> | T[]>(path, { params: listParams(query) });
  return asPage(data);
}

export function listPurchaseOrders(query: OffsetQuery) {
  return getPage<PurchaseOrder>('/api/v1/purchase-orders', query);
}

export function listSuppliers(query: OffsetQuery) {
  return getPage<Supplier>('/api/v1/suppliers', query);
}

export function listSalesOrders(query: OffsetQuery) {
  return getPage<SalesOrder>('/api/v1/sales-orders', query);
}

export function listCustomers(query: OffsetQuery) {
  return getPage<Customer>('/api/v1/customers', query);
}

export function listInvoices(query: OffsetQuery) {
  return getPage<Invoice>('/api/v1/invoices', query);
}

export function listProducts(query: OffsetQuery) {
  return getPage<Product>('/api/v1/products', query);
}

export function listManufacturingOrders(query: OffsetQuery) {
  return getPage<ProductionOrder>('/api/v1/manufacturing/orders', query);
}
