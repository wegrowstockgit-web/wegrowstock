import { apiClient } from '@/api/client';
import type { AllocationPolicy, PortalCatalogItem, PortalOrder } from '@/api/types';

/** Raw shape from GET /api/v1/portal/catalog */
export interface PortalCatalogItemRaw {
  variantId: string;
  productId: string;
  sku: string;
  productName: string;
  unitPrice: number;
  currency: string;
  primaryMediaUrl?: string | null;
}

export function mapPortalCatalogItem(raw: PortalCatalogItemRaw): PortalCatalogItem {
  return {
    id: raw.variantId,
    sku: raw.sku,
    name: raw.productName,
    unitPrice: Number(raw.unitPrice),
    currency: raw.currency,
    primaryMediaUrl: raw.primaryMediaUrl ?? null,
  };
}

export function mapPortalCatalog(items: PortalCatalogItemRaw[]): PortalCatalogItem[] {
  return items.map(mapPortalCatalogItem);
}

export interface PortalCheckoutLine {
  variantId: string;
  quantity: number;
}

export interface PortalCheckoutPayload {
  lines: PortalCheckoutLine[];
  customerPoNumber?: string;
  requestedShipDate?: string;
  allocationPolicy?: AllocationPolicy;
  quoteNotes?: string;
}

export async function createPortalOrder(payload: PortalCheckoutPayload): Promise<PortalOrder> {
  const res = await apiClient.post<PortalOrder>('/api/v1/portal/orders', payload);
  return res.data;
}

export async function requestPortalQuote(payload: PortalCheckoutPayload): Promise<PortalOrder> {
  const res = await apiClient.post<PortalOrder>('/api/v1/portal/quotes', payload);
  return res.data;
}

export async function acceptPortalQuote(orderId: string): Promise<PortalOrder> {
  const res = await apiClient.post<PortalOrder>(`/api/v1/portal/orders/${orderId}/accept-quote`);
  return res.data;
}

export interface WholesaleApplyPayload {
  companyName: string;
  taxId: string;
  contactName: string;
  email: string;
  phone?: string;
  tenantSlug?: string;
}

export interface WholesaleApplication {
  id: string;
  companyName: string;
  taxId: string;
  contactName: string;
  email: string;
  phone?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | string;
  createdAt: string;
  customerId?: string | null;
  magicToken?: string | null;
}

function tenantSlug(): string | undefined {
  const slug = import.meta.env.VITE_TENANT_SLUG as string | undefined;
  return slug && slug.trim() ? slug.trim() : undefined;
}

function tenantHeaders(): Record<string, string> {
  const slug = tenantSlug();
  return slug ? { 'X-Tenant-Slug': slug } : {};
}

export async function applyForWholesale(payload: WholesaleApplyPayload): Promise<WholesaleApplication> {
  const res = await apiClient.post<WholesaleApplication>(
    '/api/v1/showroom/apply',
    { ...payload, tenantSlug: payload.tenantSlug ?? tenantSlug() },
    { headers: tenantHeaders() },
  );
  return res.data;
}

export async function fetchPublicShowroomCatalog(): Promise<PortalCatalogItem[]> {
  const slug = tenantSlug();
  const qs = slug ? `?tenantSlug=${encodeURIComponent(slug)}` : '';
  const res = await apiClient.get<PortalCatalogItemRaw[]>(`/api/v1/showroom/catalog${qs}`, {
    headers: tenantHeaders(),
  });
  return mapPortalCatalog(res.data);
}

export async function listWholesaleApplications(status = 'PENDING'): Promise<WholesaleApplication[]> {
  const res = await apiClient.get<WholesaleApplication[]>('/api/v1/customers/applications', {
    params: { status },
  });
  return res.data;
}

export async function approveWholesaleApplication(id: string): Promise<WholesaleApplication> {
  const res = await apiClient.post<WholesaleApplication>(`/api/v1/customers/applications/${id}/approve`);
  return res.data;
}

export async function requestShowroomMagicLink(email: string): Promise<{ status: string; magicToken?: string }> {
  const res = await apiClient.post<{ status: string; magicToken?: string }>('/api/v1/auth/magic-login', { email });
  return res.data;
}
