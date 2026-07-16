import { useCallback, useEffect, useMemo, useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Package, Plus, Search, Settings2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, ProductVariant, VariantUomConversion } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { SavedFilterViews } from '@/components/ui/SavedFilterViews';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { InlineEditableCell } from '@/components/ui/InlineEditableCell';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { ProductMediaDropZone } from '@/components/ui/ProductMediaDropZone';
import {
  VirtualizedTable,
  type VirtualizedColumnDef,
} from '@/components/ui/primitives/VirtualizedTable';
import { VariantThumb } from '@/components/ui/VariantThumb';
import { useSessionStore } from '@/stores/session';

function qty(value: number | null | undefined): string {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return String(value);
}

function AddProductModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [barcode, setBarcode] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      const productRes = await apiClient.post<{ id: string }>('/api/v1/products', {
        skuRoot: sku.split('-')[0] || sku,
        name,
      });
      await apiClient.post('/api/v1/variants', {
        productId: productRes.data.id,
        sku,
        barcode: barcode || undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['products'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setName('');
      setSku('');
      setBarcode('');
      onClose();
    },
    onError: () => setError('Could not create product. Check SKU is unique.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add product" description="Creates a product with one variant">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input label="Product name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Input label="SKU" value={sku} onChange={(e) => setSku(e.target.value)} required placeholder="WIDGET-001" />
        <Input label="Barcode" value={barcode} onChange={(e) => setBarcode(e.target.value)} placeholder="Optional" />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending} disabled={!name || !sku}>Add product</Button>
        </div>
      </form>
    </Modal>
  );
}

function UomEditModal({
  variant,
  open,
  onClose,
}: {
  variant: ProductVariant | null;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [purchasingUnit, setPurchasingUnit] = useState('Case');
  const [purchasingRatio, setPurchasingRatio] = useState('24');
  const [salesUnit, setSalesUnit] = useState('Pack');
  const [salesRatio, setSalesRatio] = useState('6');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open || !variant) return;
    setError('');
    void (async () => {
      try {
        const res = await apiClient.get<VariantUomConversion[]>(`/api/v1/variants/${variant.id}/uom`);
        const purchasing = res.data.find((c) => c.uomType === 'PURCHASING');
        const sales = res.data.find((c) => c.uomType === 'SALES');
        if (purchasing) {
          setPurchasingUnit(purchasing.unitName);
          setPurchasingRatio(String(purchasing.conversionRatio));
        }
        if (sales) {
          setSalesUnit(sales.unitName);
          setSalesRatio(String(sales.conversionRatio));
        }
      } catch {
        setError('Could not load unit conversions.');
      }
    })();
  }, [open, variant?.id]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!variant) return;
      await apiClient.put(`/api/v1/variants/${variant.id}/uom`, [
        { uomType: 'STANDARD', unitName: 'EA', conversionRatio: 1 },
        { uomType: 'PURCHASING', unitName: purchasingUnit, conversionRatio: Number(purchasingRatio) },
        { uomType: 'SALES', unitName: salesUnit, conversionRatio: Number(salesRatio) },
      ]);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['products'] });
      onClose();
    },
    onError: () => setError('Could not save unit conversions.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Units of measure" description={`${variant?.sku ?? ''} — standard stock is EA`}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          saveMutation.mutate();
        }}
        className="space-y-4"
      >
        <div className="rounded-lg border border-border bg-surface p-3">
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Purchasing</p>
          <p className="mt-1 text-sm text-text-muted">PO receipts multiply by this ratio before hitting the ledger.</p>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Input label="Unit name" value={purchasingUnit} onChange={(e) => setPurchasingUnit(e.target.value)} required />
            <Input label="EA per unit" type="number" min="0.0001" step="any" value={purchasingRatio} onChange={(e) => setPurchasingRatio(e.target.value)} required />
          </div>
        </div>
        <div className="rounded-lg border border-border bg-surface p-3">
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Sales</p>
          <p className="mt-1 text-sm text-text-muted">Sell in packs — ratio defines EA per sales unit.</p>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Input label="Unit name" value={salesUnit} onChange={(e) => setSalesUnit(e.target.value)} required />
            <Input label="EA per unit" type="number" min="0.0001" step="any" value={salesRatio} onChange={(e) => setSalesRatio(e.target.value)} required />
          </div>
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={saveMutation.isPending}>Save UoM</Button>
        </div>
      </form>
    </Modal>
  );
}

function ExternalSyncToggle({
  variantId,
  enabled,
  supported,
}: {
  variantId: string;
  enabled: boolean;
  supported: boolean;
}) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: async (externalSyncEnabled: boolean) => {
      await apiClient.patch(`/api/v1/variants/${variantId}`, { externalSyncEnabled });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });

  if (!supported) {
    return <span className="text-xs text-text-muted">—</span>;
  }

  return (
    <label className="inline-flex cursor-pointer items-center gap-2">
      <input
        type="checkbox"
        checked={enabled}
        disabled={mutation.isPending}
        onChange={(e) => mutation.mutate(e.target.checked)}
        className="h-4 w-4 rounded border-border accent-accent"
      />
    </label>
  );
}

export function ProductsPage() {
  const queryClient = useQueryClient();
  const canManage = useSessionStore((s) => s.canManageInventory());
  const [search, setSearch] = useState('');
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [uomVariant, setUomVariant] = useState<ProductVariant | null>(null);
  const [peekProductId, setPeekProductId] = useState<string | null>(null);

  const {
    data,
    isLoading,
    isError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['products', search],
    queryFn: async ({ pageParam }) => {
      const params = new URLSearchParams();
      if (search) params.set('q', search);
      if (pageParam) params.set('cursor', pageParam as string);
      params.set('limit', '50');
      const res = await apiClient.get<PaginatedResponse<ProductVariant>>(
        `/api/v1/variants?${params}`
      );
      return res.data;
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor,
    retry: false,
  });

  const products = data?.pages.flatMap((p) => p.items) ?? [];
  const peekProduct = products.find((p) => p.id === peekProductId) ?? null;
  const displayed = useMemo(() => {
    if (!lowStockOnly) return products;
    return products.filter((p) => (p.reorderPoint ?? 0) > 0 && p.atp < (p.reorderPoint ?? 0));
  }, [products, lowStockOnly]);
  const syncSupported = products.some((p) => p.externalSyncEnabled !== undefined);

  const reorderMutation = useMutation({
    mutationFn: async ({ id, reorderPoint }: { id: string; reorderPoint: number }) => {
      await apiClient.patch(`/api/v1/variants/${id}`, { reorderPoint });
    },
    onMutate: async ({ id, reorderPoint }) => {
      await queryClient.cancelQueries({ queryKey: ['products'] });
      const prev = queryClient.getQueryData(['products', search]);
      queryClient.setQueryData(['products', search], (old: typeof data) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            items: page.items.map((item) =>
              item.id === id ? { ...item, reorderPoint } : item
            ),
          })),
        };
      });
      return { prev };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(['products', search], ctx.prev);
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['products'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const loadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      void fetchNextPage();
    }
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  const columns = useMemo((): VirtualizedColumnDef<ProductVariant>[] => {
    const cols: VirtualizedColumnDef<ProductVariant>[] = [
      {
        id: 'thumb',
        header: '',
        width: 48,
        hideable: false,
        align: 'center',
        cell: (product) => (
          <div className="flex justify-center">
            <VariantThumb url={product.primaryMediaUrl} alt={product.name} size="sm" />
          </div>
        ),
      },
      {
        id: 'sku',
        header: 'SKU',
        width: 128,
        sortable: true,
        sortValue: (product) => product.sku,
        cell: (product) => (
          <span className="truncate font-mono text-text">{product.sku}</span>
        ),
      },
      {
        id: 'name',
        header: 'Name',
        width: 180,
        sortable: true,
        sortValue: (product) => product.name,
        cell: (product) => <span className="truncate text-text">{product.name}</span>,
      },
      {
        id: 'barcode',
        header: 'Barcode',
        width: 120,
        sortable: true,
        sortValue: (product) => product.barcode ?? '',
        cell: (product) => (
          <span className="truncate font-mono text-text-muted">{product.barcode ?? '—'}</span>
        ),
      },
      {
        id: 'onHand',
        header: 'On hand',
        width: 88,
        align: 'right',
        sortable: true,
        sortValue: (product) => product.onHand ?? 0,
        cell: (product) => (
          <span className="font-mono tabular-nums text-text">{qty(product.onHand)}</span>
        ),
      },
      {
        id: 'allocated',
        header: 'Allocated',
        width: 88,
        align: 'right',
        sortable: true,
        sortValue: (product) => product.allocated ?? 0,
        cell: (product) => (
          <span className="font-mono tabular-nums text-text-muted">{qty(product.allocated)}</span>
        ),
      },
      {
        id: 'atp',
        header: 'ATP',
        width: 80,
        align: 'right',
        sortable: true,
        sortValue: (product) => product.atp ?? 0,
        cell: (product) => (
          <span className="font-mono font-medium tabular-nums text-text">{qty(product.atp)}</span>
        ),
      },
      {
        id: 'reorder',
        header: 'Reorder',
        width: 96,
        align: 'right',
        sortable: true,
        sortValue: (product) => product.reorderPoint ?? 0,
        cell: (product) =>
          canManage ? (
            <span onClick={(e) => e.stopPropagation()}>
              <InlineEditableCell
                value={product.reorderPoint ?? 0}
                inputType="number"
                onSave={async (val) => {
                  await reorderMutation.mutateAsync({
                    id: product.id,
                    reorderPoint: Number(val),
                  });
                }}
              />
            </span>
          ) : (
            <span className="font-mono tabular-nums">{qty(product.reorderPoint)}</span>
          ),
      },
    ];

    if (canManage) {
      cols.push({
        id: 'uom',
        header: 'UoM',
        width: 56,
        align: 'center',
        cell: (product) => (
          <div className="flex justify-center" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              onClick={() => setUomVariant(product)}
              className="inline-flex items-center justify-center rounded p-1 text-text-muted hover:bg-surface-overlay hover:text-accent"
              aria-label={`Edit UoM for ${product.sku}`}
            >
              <Settings2 className="h-4 w-4" />
            </button>
          </div>
        ),
      });
    }

    if (syncSupported) {
      cols.push({
        id: 'channelSync',
        header: 'Channel sync',
        width: 88,
        align: 'center',
        cell: (product) => (
          <div className="flex justify-center" onClick={(e) => e.stopPropagation()}>
            {canManage ? (
              <ExternalSyncToggle
                variantId={product.id}
                enabled={product.externalSyncEnabled ?? true}
                supported={product.externalSyncEnabled !== undefined}
              />
            ) : (
              <span className="text-xs text-text-muted">
                {product.externalSyncEnabled ? 'On' : 'Off'}
              </span>
            )}
          </div>
        ),
      });
    }

    return cols;
  }, [canManage, reorderMutation, syncSupported]);

  const columnItems = useMemo(
    () =>
      columns
        .filter((c) => c.hideable !== false && c.id !== 'thumb')
        .map((c) => ({
          id: c.id,
          label: typeof c.header === 'string' && c.header ? c.header : c.id,
        })),
    [columns],
  );

  if (isLoading) {
    return (
      <div className="p-6">
        <TableSkeleton rows={12} cols={6} />
      </div>
    );
  }

  if (isError && products.length === 0) {
    return (
      <div className="p-6">
        <EmptyState
          icon={Package}
          title="Unable to load products"
          description="Check your connection and try again."
          action={<Button onClick={() => refetch()}>Retry</Button>}
        />
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100dvh-var(--header-height))] max-h-[calc(100dvh-var(--header-height))] min-h-0 flex-col overflow-hidden">
      <div className="mb-0 flex shrink-0 flex-col gap-4 border-b border-border/60 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Products</h1>
          <p className="mt-1 text-sm text-text-muted">
            {displayed.length} variants loaded
          </p>
        </div>
        <div className="flex gap-3">
          <div className="relative w-full sm:w-64">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter by SKU or name..."
              className="pl-9"
            />
          </div>
          {canManage && (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              Add product
            </Button>
          )}
        </div>
      </div>

      <div className="shrink-0 px-6 pt-4">
        <DataListToolbar columnItems={columnItems}>
          <SavedFilterViews
            className="mb-0"
            storageKey="products-filters"
            activeFilters={{ lowStock: lowStockOnly ? '1' : '' }}
            onApply={(f) => setLowStockOnly(f.lowStock === '1')}
            defaultPresets={[
              { id: 'all', label: 'All', filters: {} },
              { id: 'low', label: 'Low stock', filters: { lowStock: '1' } },
            ]}
          />
        </DataListToolbar>
      </div>

      <VirtualizedTable
        columns={columns}
        rows={displayed}
        getRowId={(row) => row.id}
        selectedRowId={peekProductId}
        onRowClick={(row) => setPeekProductId(row.id)}
        onEndReached={loadMore}
        empty={
          <div className="p-6">
            <EmptyState
              icon={Package}
              title="No products yet"
              description="Add your first product to start receiving and selling stock."
              action={
                canManage ? (
                  <Button onClick={() => setModalOpen(true)}>
                    <Plus className="h-4 w-4" />
                    Add your first product
                  </Button>
                ) : undefined
              }
            />
          </div>
        }
      />

      {isFetchingNextPage && (
        <div className="border-t border-border p-3 text-center text-sm text-text-muted">
          Loading more...
        </div>
      )}

      <AddProductModal open={modalOpen} onClose={() => setModalOpen(false)} />
      <UomEditModal variant={uomVariant} open={uomVariant !== null} onClose={() => setUomVariant(null)} />

      <RightPeekDrawer
        open={!!peekProductId}
        onClose={() => setPeekProductId(null)}
        title={peekProduct?.sku ?? 'Product'}
        description={peekProduct?.name}
      >
        {peekProduct ? (
          <dl className="space-y-3 text-sm">
            {canManage && (
              <div data-testid="product-media-picker" className="space-y-3">
                <dt className="mb-2 text-text-muted">Product photo</dt>
                <dd className="space-y-3">
                  <MediaPicker
                    kind="PRODUCT"
                    label="Upload product photo"
                    capture
                    previewUrl={peekProduct.primaryMediaUrl}
                    onUploaded={async (result) => {
                      await apiClient.post(`/api/v1/products/variants/${peekProduct.id}/media`, {
                        url: result.contentUrl,
                        isPrimary: true,
                      });
                      void queryClient.invalidateQueries({ queryKey: ['products'] });
                    }}
                  />
                  <ProductMediaDropZone
                    variantId={peekProduct.id}
                    onUploaded={async () => {
                      void queryClient.invalidateQueries({ queryKey: ['products'] });
                    }}
                  />
                </dd>
              </div>
            )}
            <div className="flex justify-between gap-4">
              <dt className="text-text-muted">Barcode</dt>
              <dd className="font-mono">{peekProduct.barcode ?? '—'}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-text-muted">On hand</dt>
              <dd className="font-mono tabular-nums">{qty(peekProduct.onHand)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-text-muted">Allocated</dt>
              <dd className="font-mono tabular-nums">{qty(peekProduct.allocated)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-text-muted">ATP</dt>
              <dd className="font-mono tabular-nums font-semibold">{qty(peekProduct.atp)}</dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-text-muted">Reorder point</dt>
              <dd className="w-28">
                {canManage ? (
                  <InlineEditableCell
                    value={peekProduct.reorderPoint ?? 0}
                    inputType="number"
                    onSave={async (val) => {
                      await reorderMutation.mutateAsync({
                        id: peekProduct.id,
                        reorderPoint: Number(val),
                      });
                    }}
                  />
                ) : (
                  <span className="font-mono tabular-nums">{qty(peekProduct.reorderPoint)}</span>
                )}
              </dd>
            </div>
          </dl>
        ) : (
          <p className="text-sm text-text-muted">Loading…</p>
        )}
      </RightPeekDrawer>
    </div>
  );
}
