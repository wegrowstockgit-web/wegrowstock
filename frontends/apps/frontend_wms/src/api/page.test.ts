import { describe, expect, it } from 'vitest';
import { asPage, unwrapPageItems } from './page';

describe('page helpers', () => {
  it('unwraps arrays and page envelopes', () => {
    expect(unwrapPageItems([{ id: '1' }])).toEqual([{ id: '1' }]);
    expect(unwrapPageItems({ items: [{ id: '2' }] })).toEqual([{ id: '2' }]);
    expect(unwrapPageItems(undefined)).toEqual([]);
  });

  it('normalizes a Spring PageResponse', () => {
    const page = asPage({
      items: [{ id: 'a' }],
      totalElements: 11,
      totalPages: 3,
      page: 2,
      size: 5,
      hasMore: true,
    });
    expect(page.totalElements).toBe(11);
    expect(page.page).toBe(2);
    expect(page.hasMore).toBe(true);
  });

  it('wraps a legacy array as a single page', () => {
    const page = asPage([{ id: 'legacy' }]);
    expect(page.items).toHaveLength(1);
    expect(page.totalElements).toBe(1);
    expect(page.totalPages).toBe(1);
  });

  it('returns an empty page for missing payloads', () => {
    expect(asPage(undefined).items).toEqual([]);
    expect(asPage(null).totalElements).toBe(0);
  });

  it('falls back to total when totalElements is omitted', () => {
    const page = asPage({ items: [{ id: 'x' }], total: 9 });
    expect(page.totalElements).toBe(9);
    expect(page.hasMore).toBe(false);
  });
});
