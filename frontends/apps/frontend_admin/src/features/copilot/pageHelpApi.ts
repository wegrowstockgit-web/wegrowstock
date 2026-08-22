import { apiClient } from '@/lib/apiClient';

export type MistakeFix = {
  mistake: string;
  solution: string;
  requiredRole: string;
};

export type PageHelpRecord = {
  id: string;
  routePattern: string;
  category: string;
  title: string;
  summary: string;
  rolePrivileges: string;
  keyActions: string[];
  commonMistakes: MistakeFix[];
  proTip?: string | null;
  updatedAt?: string | null;
};

export type PageHelpWritePayload = Omit<PageHelpRecord, 'id' | 'updatedAt'>;

const BASE = '/api/v1/control-plane/page-knowledge';

export async function fetchPageHelp(search = '', category = ''): Promise<PageHelpRecord[]> {
  const { data } = await apiClient.get<PageHelpRecord[]>(BASE, {
    params: { search: search || undefined, category: category || undefined },
  });
  return Array.isArray(data) ? data : [];
}

export async function createPageHelp(payload: PageHelpWritePayload): Promise<PageHelpRecord> {
  const { data } = await apiClient.post<PageHelpRecord>(BASE, payload);
  return data;
}

export async function updatePageHelp(id: string, payload: PageHelpWritePayload): Promise<PageHelpRecord> {
  const { data } = await apiClient.put<PageHelpRecord>(`${BASE}/${id}`, payload);
  return data;
}

export async function deletePageHelp(id: string): Promise<void> {
  await apiClient.delete(`${BASE}/${id}`);
}

export const PAGE_HELP_CATEGORIES = [
  'Core',
  'Inbound',
  'Fulfillment',
  'Inventory',
  'Manufacturing',
  'Field',
  'Sales',
  'Showroom',
  'Settings',
  'Platform',
] as const;
