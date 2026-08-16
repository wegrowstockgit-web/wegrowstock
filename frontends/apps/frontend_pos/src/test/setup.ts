import '@testing-library/jest-dom/vitest';
import 'fake-indexeddb/auto';
import { afterEach, beforeEach, vi } from 'vitest';
import { db } from '@/lib/db';
import { POS_SESSION_CACHE_KEY } from '@/lib/posSession';

Object.defineProperty(navigator, 'language', { configurable: true, value: 'en-US' });
Object.defineProperty(navigator, 'languages', { configurable: true, value: ['en-US'] });

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({}),
    }),
  );
});

afterEach(async () => {
  await db.catalog_cache.clear();
  await db.cart_drafts.clear();
  await db.outbox_receipts.clear();
  localStorage.removeItem(POS_SESSION_CACHE_KEY);
  vi.unstubAllGlobals();
});
