import { useRef, useState, useEffect, useMemo } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Package, Plus, Search, Settings2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { PaginatedResponse, ProductVariant, VariantUomConversion } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { EmptyState } from '@/components/ui/EmptyState';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { SavedFilterViews } from '@/components/ui/SavedFilterViews';
import { InlineEditableCell } from '@/components/ui/InlineEditableCell';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { VariantThumb } from '@/components/ui/VariantThumb';
import { useSessionStore } from '@/stores/session';
import { cn } from '@/lib/utils';

const ROW_HEIGHT = 48;

function qty(value: number | null | undefined): string {
  if (value == null || Number.isNaN(Number(value))) return '—';
  return String(value);
}

function productsGridClass(canManage: boolean, syncSupported: boolean) {
  return cn(
    'grid w-full items-center gap-x-2 px-4',
    canManage && syncSupported && 'grid-cols-[2.5rem_minmax(6.5rem,1.1fr)_minmax(7rem,1.4fr)_minmax(5.5rem,1fr)_repeat(4,minmax(4.25rem,0.7fr))_3.25rem_3.5rem]',
    canManage && !syncSupported && 'grid-cols-[2.5rem_minmax(6.5rem,1.2fr)_minmax(7rem,1.5fr)_minmax(5.5rem,1.1fr)_repeat(4,minmax(4.25rem,0.75fr))_3.25rem]',
    !canManage && syncSupported && 'grid-cols-[2.5rem_minmax(6.5rem,1.1fr)_minmax(7rem,1.4fr)_minmax(5.5rem,1fr)_repeat(4,minmax(4.25rem,0.7fr))_3.5rem]',
    !canManage && !syncSupported && 'grid-cols-[2.5rem_minmax(6.5rem,1.2fr)_minmax(7rem,1.6fr)_minmax(5.5rem,1.1fr)_repeat(4,minmax(4.5rem,0.8fr))]'
  );
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
  const parentRef = useRef<HTMLDivElement>(null);

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

  const virtualizer = useVirtualizer({
    count: displayed.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  });

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
    <div className="flex h-full min-h-0 flex-col">
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
      <SavedFilterViews
        storageKey="products-filters"
        activeFilters={{ lowStock: lowStockOnly ? '1' : '' }}
        onApply={(f) => setLowStockOnly(f.lowStock === '1')}
        defaultPresets={[
          { id: 'all', label: 'All', filters: {} },
          { id: 'low', label: 'Low stock', filters: { lowStock: '1' } },
        ]}
      />
      </div>

      {displayed.length === 0 ? (
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
      ) : (
        <div className="min-h-0 flex-1 overflow-hidden bg-surface-raised">
          <div
            className={cn(
              productsGridClass(canManage, syncSupported),
              'sticky top-0 z-10 h-11 border-b border-border bg-surface-overlay px-4 text-xs font-medium uppercase tracking-wide text-text-muted'
            )}
            role="row"
          >
            <div aria-hidden />
            <div>SKU</div>
            <div>Name</div>
            <div>Barcode</div>
            <div className="text-right">On hand</div>
            <div className="text-right">Allocated</div>
            <div className="text-right">ATP</div>
            <div className="text-right">Reorder</div>
            {canManage && <div className="text-center">UoM</div>}
            {syncSupported && <div className="text-center">Channel sync</div>}
          </div>

          <div ref={parentRef} className="h-[calc(100vh-16rem)] overflow-auto">
            <div
              style={{ height: `${virtualizer.getTotalSize()}px`, position: 'relative' }}
            >
              {virtualizer.getVirtualItems().map((virtualRow) => {
                const product = displayed[virtualRow.index];
                if (!product) return null;

                if (
                  virtualRow.index >= displayed.length - 5 &&
                  hasNextPage &&
                  !isFetchingNextPage
                ) {
                  void fetchNextPage();
                }

                return (
                  <div
                    key={product.id}
                    className={cn(
                      productsGridClass(canManage, syncSupported),
                      'absolute left-0 top-0 cursor-pointer border-b border-border text-sm hover:bg-surface-overlay'
                    )}
                    style={{
                      height: `${virtualRow.size}px`,
                      transform: `translateY(${virtualRow.start}px)`,
                    }}
                    role="row"
                    onClick={() => setPeekProductId(product.id)}
                  >
                    <div className="flex justify-center">
                      <VariantThumb url={product.primaryMediaUrl} alt={product.name} size="sm" />
                    </div>
                    <div className="truncate font-mono text-text">{product.sku}</div>
                    <div className="truncate text-text">{product.name}</div>
                    <div className="truncate font-mono text-text-muted">
                      {product.barcode ?? '—'}
                    </div>
                    <div className="text-right font-mono tabular-nums text-text">
                      {qty(product.onHand)}
                    </div>
                    <div className="text-right font-mono tabular-nums text-text-muted">
                      {qty(product.allocated)}
                    </div>
                    <div className="text-right font-mono text-sm font-medium tabular-nums text-text">
                      {qty(product.atp)}
                    </div>
                    <div className="text-right" onClick={(e) => e.stopPropagation()}>
                      {canManage ? (
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
                      ) : (
                        <span className="font-mono tabular-nums">{qty(product.reorderPoint)}</span>
                      )}
                    </div>
                    {canManage && (
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
                    )}
                    {syncSupported && (
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
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {isFetchingNextPage && (
            <div className="border-t border-border p-3 text-center text-sm text-text-muted">
              Loading more...
            </div>
          )}
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
