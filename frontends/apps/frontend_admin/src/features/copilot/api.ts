import { apiClient } from '@/lib/apiClient';

export type KnowledgeDocument = {
  id: string;
  title: string;
  slug: string;
  chunkCount: number;
  createdAt: string;
};

export async function fetchKnowledgeDocuments(): Promise<KnowledgeDocument[]> {
  const { data } = await apiClient.get<KnowledgeDocument[]>('/api/v1/control-plane/knowledge');
  return data;
}

export async function ingestKnowledgeDocument(file: File): Promise<KnowledgeDocument> {
  const form = new FormData();
  form.append('file', file);
  const { data } = await apiClient.post<KnowledgeDocument>(
    '/api/v1/control-plane/knowledge/ingest',
    form,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      transformRequest: [
        (body, headers) => {
          if (body instanceof FormData && headers) {
            delete headers['Content-Type'];
          }
          return body;
        },
      ],
    },
  );
  return data;
}

export async function deleteKnowledgeDocument(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/control-plane/knowledge/${id}`);
}
