import { apiClient } from '@/api/client';

export interface MeshDiscoverListing {
  variantId: string;
  productName: string;
  imageUrl?: string | null;
  sellerName: string;
  sellerTenantId: string;
}

export interface MeshNetworkRelationship {
  id: string;
  partnerTenantId: string;
  partnerName: string;
  role: 'BUYER' | 'SELLER' | string;
  displayStatus: 'PENDING' | 'REQUESTED' | 'CONNECTED' | string;
  connectionStatus: string;
  supplierId?: string | null;
  customerId?: string | null;
  canApprove: boolean;
}

export interface MeshSharedCatalogRow {
  variantId: string;
  sku: string;
  productName: string;
  published: boolean;
  meshWholesalePrice?: number | null;
}

export interface MeshConnection {
  id: string;
  tenantId: string;
  partnerTenantId: string;
  supplierId?: string | null;
  customerId?: string | null;
  connectionStatus: string;
}

export interface MeshSourcingSuggestion {
  variantId: string;
  productName: string;
  sku: string;
  partnerTenantId: string;
  partnerName: string;
  supplierId?: string | null;
  meshPartnerSku: string;
}

export async function fetchMeshDiscover(): Promise<MeshDiscoverListing[]> {
  const res = await apiClient.get<MeshDiscoverListing[]>('/api/v1/mesh/discover');
  return res.data;
}

export async function fetchMeshNetwork(): Promise<MeshNetworkRelationship[]> {
  const res = await apiClient.get<MeshNetworkRelationship[]>('/api/v1/mesh/network');
  return res.data;
}

export async function fetchMeshSharedCatalog(): Promise<MeshSharedCatalogRow[]> {
  const res = await apiClient.get<MeshSharedCatalogRow[]>('/api/v1/mesh/catalog');
  return res.data;
}

export async function updateMeshListing(
  variantId: string,
  published: boolean,
  meshWholesalePrice?: number | null,
): Promise<MeshSharedCatalogRow> {
  const res = await apiClient.put<MeshSharedCatalogRow>(`/api/v1/mesh/catalog/${variantId}`, {
    published,
    meshWholesalePrice: meshWholesalePrice ?? null,
  });
  return res.data;
}

export async function requestMeshConnection(payload: {
  partnerTenantId?: string;
  variantId?: string;
}): Promise<MeshConnection> {
  const res = await apiClient.post<MeshConnection>('/api/v1/mesh/connections/request', payload);
  return res.data;
}

export async function approveMeshConnection(id: string): Promise<MeshConnection> {
  const res = await apiClient.post<MeshConnection>(`/api/v1/mesh/connections/${id}/approve`);
  return res.data;
}

export async function fetchMeshSourcingSuggestions(): Promise<MeshSourcingSuggestion[]> {
  const res = await apiClient.get<MeshSourcingSuggestion[]>('/api/v1/dashboard/mesh-sourcing-suggestions');
  return res.data;
}

export function draftPoPath(suggestion: Pick<MeshSourcingSuggestion, 'meshPartnerSku' | 'supplierId'>): string {
  const params = new URLSearchParams({ meshPartnerSku: suggestion.meshPartnerSku });
  if (suggestion.supplierId) {
    params.set('supplierId', suggestion.supplierId);
  }
  return `/purchase-orders/new?${params.toString()}`;
}
