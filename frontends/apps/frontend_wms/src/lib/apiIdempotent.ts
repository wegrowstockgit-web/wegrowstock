import { apiClient } from '@/api/client';
import { generateIdempotencyKey } from '@/lib/utils';

export { generateIdempotencyKey };

export async function postIdempotent<T = unknown>(
  url: string,
  body?: unknown,
  key = generateIdempotencyKey()
): Promise<T> {
  const res = await apiClient.post<T>(url, body, {
    headers: { 'Idempotency-Key': key },
  });
  return res.data;
}
