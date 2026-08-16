import { db, type CatalogCacheRow } from './db';

export const DEMO_CATALOG: CatalogCacheRow[] = [
  {
    id: 'a0000000-0000-4000-8000-000000000701',
    upc: '7501234567890',
    name: 'Agua 600ml',
    unitPrice: 12.5,
    imageUrl: '',
  },
  {
    id: 'a0000000-0000-4000-8000-000000000702',
    upc: '049000042566',
    name: 'Cola 355ml',
    unitPrice: 18,
  },
  {
    id: 'a0000000-0000-4000-8000-000000000703',
    upc: '022000001234',
    name: 'Bread loaf',
    unitPrice: 29.9,
  },
];

export async function seedDemoCatalogIfEmpty(): Promise<number> {
  const count = await db.catalog_cache.count();
  if (count > 0) return 0;
  await db.catalog_cache.bulkPut(DEMO_CATALOG);
  return DEMO_CATALOG.length;
}
