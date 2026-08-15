import { apiClient } from '@/api/client';
import type {
  Bom,
  Customer,
  Invoice,
  PaginatedResponse,
  ProductVariant,
  ProductionOrder,
  PurchaseOrder,
  Return,
  SalesOrder,
  Supplier,
} from '@/api/types';

export interface GlobalSearchResult {
  id: string;
  label: string;
  sublabel?: string;
  category: string;
  path: string;
}

function matchesQuery(query: string, ...values: (string | undefined)[]): boolean {
  const q = query.toLowerCase();
  return values.some((value) => value?.toLowerCase().includes(q));
}

function settledData<T>(result: PromiseSettledResult<{ data: T }>): T | null {
  return result.status === 'fulfilled' ? result.value.data : null;
}

export async function globalSearch(query: string): Promise<GlobalSearchResult[]> {
  const q = query.trim();
  if (q.length < 2) return [];

  const [
    variantsRes,
    salesOrdersRes,
    customersRes,
    suppliersRes,
    purchaseOrdersRes,
    invoicesRes,
    returnsRes,
    productionOrdersRes,
    bomsRes,
  ] = await Promise.allSettled([
    apiClient.get<PaginatedResponse<ProductVariant>>(
      `/api/v1/variants?q=${encodeURIComponent(q)}&limit=10`
    ),
    apiClient.get<SalesOrder[]>('/api/v1/sales-orders'),
    apiClient.get<Customer[]>('/api/v1/customers'),
    apiClient.get<Supplier[]>('/api/v1/suppliers'),
    apiClient.get<PurchaseOrder[]>('/api/v1/purchase-orders'),
    apiClient.get<Invoice[]>('/api/v1/invoices'),
    apiClient.get<Return[]>('/api/v1/returns'),
    apiClient.get<ProductionOrder[]>('/api/v1/manufacturing/orders'),
    apiClient.get<Bom[]>('/api/v1/manufacturing/boms'),
  ]);

  const results: GlobalSearchResult[] = [];

  const variants = settledData(variantsRes)?.items ?? [];
  for (const variant of variants) {
    results.push({
      id: `variant-${variant.id}`,
      label: variant.name,
      sublabel: variant.sku,
      category: 'Product',
      path: '/products',
    });
  }

  const salesOrders = settledData(salesOrdersRes) ?? [];
  for (const order of salesOrders.filter((item) => matchesQuery(q, item.number, item.customerName))) {
    results.push({
      id: `so-${order.id}`,
      label: order.number,
      sublabel: order.customerName,
      category: 'Sales order',
      path: '/sales-orders',
    });
  }

  const customers = settledData(customersRes) ?? [];
  for (const customer of customers.filter((item) => matchesQuery(q, item.name, item.email))) {
    results.push({
      id: `customer-${customer.id}`,
      label: customer.name,
      sublabel: customer.email,
      category: 'Customer',
      path: '/customers',
    });
  }

  const suppliers = settledData(suppliersRes) ?? [];
  for (const supplier of suppliers.filter((item) => matchesQuery(q, item.name, item.contactEmail))) {
    results.push({
      id: `supplier-${supplier.id}`,
      label: supplier.name,
      sublabel: supplier.contactEmail,
      category: 'Supplier',
      path: '/suppliers',
    });
  }

  const purchaseOrders = settledData(purchaseOrdersRes) ?? [];
  for (const order of purchaseOrders.filter((item) =>
    matchesQuery(q, item.number, item.supplierName)
  )) {
    results.push({
      id: `po-${order.id}`,
      label: order.number,
      sublabel: order.supplierName,
      category: 'Purchase order',
      path: '/purchase-orders',
    });
  }

  const invoices = settledData(invoicesRes) ?? [];
  for (const invoice of invoices.filter((item) =>
    matchesQuery(q, item.number, item.customerName)
  )) {
    results.push({
      id: `invoice-${invoice.id}`,
      label: invoice.number,
      sublabel: invoice.customerName,
      category: 'Invoice',
      path: '/invoices',
    });
  }

  const returns = settledData(returnsRes) ?? [];
  for (const ret of returns.filter((item) =>
    matchesQuery(q, item.number, item.customerName, item.salesOrderNumber)
  )) {
    results.push({
      id: `return-${ret.id}`,
      label: ret.number,
      sublabel: ret.customerName ?? ret.salesOrderNumber,
      category: 'Return',
      path: '/returns',
    });
  }

  const productionOrders = settledData(productionOrdersRes) ?? [];
  for (const order of productionOrders.filter((item) =>
    matchesQuery(q, item.number, item.parentSku, item.parentName)
  )) {
    results.push({
      id: `mo-${order.id}`,
      label: order.number,
      sublabel: order.parentName ?? order.parentSku,
      category: 'Manufacturing',
      path: '/manufacturing/orders',
    });
  }

  const boms = settledData(bomsRes) ?? [];
  for (const bom of boms.filter((item) =>
    matchesQuery(q, item.name, item.parentSku, item.parentName)
  )) {
    results.push({
      id: `bom-${bom.id}`,
      label: bom.name,
      sublabel: bom.parentName ?? bom.parentSku,
      category: 'BOM',
      path: '/manufacturing/boms',
    });
  }

  return results.slice(0, 20);
}
