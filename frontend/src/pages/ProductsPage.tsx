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
import { LedgerHistoryTable } from '@/features/inventory/LedgerHistoryTable';
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
  const [length, setLength] = useState('');
  const [width, setWidth] = useState('');
  const [height, setHeight] = useState('');
  const [weight, setWeight] = useState('');
  const [dimUnit, setDimUnit] = useState('in');
  const [weightUnit, setWeightUnit] = useState('lb');
  const [hsTariffCode, setHsTariffCode] = useState('');
  const [countryOfOrigin, setCountryOfOrigin] = useState('');
  const [isHazmat, setIsHazmat] = useState(false);
  const [palletTie, setPalletTie] = useState('');
  const [palletHigh, setPalletHigh] = useState('');
  const [storageTempZone, setStorageTempZone] = useState('AMBIENT');
  const [isFragile, setIsFragile] = useState(false);
  const [abcClassification, setAbcClassification] = useState('C');
  const [lifecycleStatus, setLifecycleStatus] = useState('ACTIVE');
  const [tradeOpen, setTradeOpen] = useState(false);
  const [handlingOpen, setHandlingOpen] = useState(false);
  const [error, setError] = useState('');

  const dimsReady =
    Number(length) > 0 && Number(width) > 0 && Number(height) > 0 && Number(weight) > 0;

  const resetForm = () => {
    setName('');
    setSku('');
    setBarcode('');
    setLength('');
    setWidth('');
    setHeight('');
    setWeight('');
    setDimUnit('in');
    setWeightUnit('lb');
    setHsTariffCode('');
    setCountryOfOrigin('');
    setIsHazmat(false);
    setPalletTie('');
    setPalletHigh('');
    setStorageTempZone('AMBIENT');
    setIsFragile(false);
    setAbcClassification('C');
    setLifecycleStatus('ACTIVE');
    setTradeOpen(false);
    setHandlingOpen(false);
  };

  const mutation = useMutation({
    mutationFn: async () => {
      // Use the full SKU as skuRoot so hyphenated SKUs (e.g. ENT-ABC123) do not
      // collide on a shared prefix under products_tenant_id_sku_root_key.
      const productRes = await apiClient.post<{ id: string }>('/api/v1/products', {
        skuRoot: sku.trim(),
        name,
      });
      await apiClient.post('/api/v1/variants', {
        productId: productRes.data.id,
        sku,
        barcode: barcode || undefined,
        length: Number(length),
        width: Number(width),
        height: Number(height),
        weight: Number(weight),
        dimUnit,
        weightUnit,
        hsTariffCode: hsTariffCode || undefined,
        countryOfOrigin: countryOfOrigin || undefined,
        isHazmat,
        palletTie: palletTie ? Number(palletTie) : undefined,
        palletHigh: palletHigh ? Number(palletHigh) : undefined,
        storageTempZone,
        isFragile,
        abcClassification,
        lifecycleStatus,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['products'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      resetForm();
      onClose();
    },
    onError: () => setError('Could not create product. Check SKU is unique and dimensions are valid.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add product" description="Creates a product with one variant">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="max-h-[70vh] space-y-4 overflow-y-auto pr-1"
      >
        <Input label="Product name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Input label="SKU" value={sku} onChange={(e) => setSku(e.target.value)} required placeholder="WIDGET-001" />
        <Input label="Barcode" value={barcode} onChange={(e) => setBarcode(e.target.value)} placeholder="Optional" />

        <div>
          <p className="mb-2 text-sm font-medium text-text">Dimensions <span className="text-danger">*</span></p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Input label="Length" type="number" min="0.0001" step="any" value={length} onChange={(e) => setLength(e.target.value)} required />
            <Input label="Width" type="number" min="0.0001" step="any" value={width} onChange={(e) => setWidth(e.target.value)} required />
            <Input label="Height" type="number" min="0.0001" step="any" value={height} onChange={(e) => setHeight(e.target.value)} required />
          </div>
          <div className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-4">
            <Input label="Weight" type="number" min="0.0001" step="any" value={weight} onChange={(e) => setWeight(e.target.value)} required />
            <Input label="Dim unit" value={dimUnit} onChange={(e) => setDimUnit(e.target.value)} required />
            <Input label="Weight unit" value={weightUnit} onChange={(e) => setWeightUnit(e.target.value)} required />
          </div>
        </div>

        <details
          className="rounded-lg border border-border"
          open={tradeOpen}
          onToggle={(e) => setTradeOpen((e.target as HTMLDetailsElement).open)}
        >
          <summary className="cursor-pointer select-none px-3 py-2 text-sm font-medium text-text">
            Trade &amp; Compliance
          </summary>
          <div className="space-y-3 border-t border-border px-3 py-3">
            <Input label="HS tariff code" value={hsTariffCode} onChange={(e) => setHsTariffCode(e.target.value)} placeholder="e.g. 8471.30" />
            <Input label="Country of origin" value={countryOfOrigin} onChange={(e) => setCountryOfOrigin(e.target.value)} placeholder="US" maxLength={2} />
            <label className="flex items-center gap-2 text-sm text-text">
              <input
                id="add-product-hazmat"
                name="isHazmat"
                type="checkbox"
                checked={isHazmat}
                onChange={(e) => setIsHazmat(e.target.checked)}
              />
              Hazmat / dangerous goods
            </label>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <label className="block text-sm">
                <span className="mb-1 block text-text-muted">ABC classification</span>
                <select
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-text"
                  value={abcClassification}
                  onChange={(e) => setAbcClassification(e.target.value)}
                >
                  <option value="A">A</option>
                  <option value="B">B</option>
                  <option value="C">C</option>
                </select>
              </label>
              <label className="block text-sm">
                <span className="mb-1 block text-text-muted">Lifecycle</span>
                <select
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-text"
                  value={lifecycleStatus}
                  onChange={(e) => setLifecycleStatus(e.target.value)}
                >
                  <option value="PRE_RELEASE">Pre-release</option>
                  <option value="ACTIVE">Active</option>
                  <option value="PHASE_OUT">Phase out</option>
                  <option value="DISCONTINUED">Discontinued</option>
                </select>
              </label>
            </div>
          </div>
        </details>

        <details
          className="rounded-lg border border-border"
          open={handlingOpen}
          onToggle={(e) => setHandlingOpen((e.target as HTMLDetailsElement).open)}
        >
          <summary className="cursor-pointer select-none px-3 py-2 text-sm font-medium text-text">
            Advanced Handling
          </summary>
          <div className="space-y-3 border-t border-border px-3 py-3">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Input label="Pallet tie (Ti)" type="number" min="1" step="1" value={palletTie} onChange={(e) => setPalletTie(e.target.value)} />
              <Input label="Pallet high (Hi)" type="number" min="1" step="1" value={palletHigh} onChange={(e) => setPalletHigh(e.target.value)} />
              <label className="block text-sm">
                <span className="mb-1 block text-text-muted">Temp zone</span>
                <select
                  className="w-full rounded-md border border-border bg-surface px-3 py-2 text-text"
                  value={storageTempZone}
                  onChange={(e) => setStorageTempZone(e.target.value)}
                >
                  <option value="AMBIENT">Ambient</option>
                  <option value="REFRIGERATED">Refrigerated</option>
                  <option value="FROZEN">Frozen</option>
                </select>
              </label>
            </div>
            <label className="flex items-center gap-2 text-sm text-text">
              <input
                id="add-product-fragile"
                name="isFragile"
                type="checkbox"
                checked={isFragile}
                onChange={(e) => setIsFragile(e.target.checked)}
              />
              Fragile — pick last in wave path
            </label>
          </div>
        </details>

        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending} disabled={!name || !sku || !dimsReady}>
            Add product
          </Button>
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
  search,
}: {
  variantId: string;
  enabled: boolean;
  supported: boolean;
  search: string;
}) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: async (nextEnabled: boolean) => {
      await apiClient.patch(`/api/v1/variants/${variantId}/channel-sync`, {
        enabled: nextEnabled,
      });
    },
    onMutate: async (nextEnabled) => {
      await queryClient.cancelQueries({ queryKey: ['products'] });
      const prev = queryClient.getQueryData(['products', search]);
      queryClient.setQueryData(['products', search], (old: unknown) => {
        const data = old as
          | {
              pages: Array<{ items: ProductVariant[] }>;
              pageParams: unknown[];
            }
          | undefined;
        if (!data) return old;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.map((item) =>
              item.id === variantId ? { ...item, externalSyncEnabled: nextEnabled } : item,
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
    },
  });

  if (!supported) {
    return <span className="text-xs text-text-muted">—</span>;
  }

  return (
    <label className="inline-flex cursor-pointer items-center gap-2" htmlFor={`channel-sync-${variantId}`}>
      <input
        id={`channel-sync-${variantId}`}
        name={`channelSync-${variantId}`}
        type="checkbox"
        data-testid={`channel-sync-${variantId}`}
        aria-label="Channel sync"
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
  const [peekTab, setPeekTab] = useState<'details' | 'ledger'>('details');

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
        width: 56,
        hideable: false,
        align: 'center',
        cell: (product) => (
          <div
            className="mx-auto flex h-12 w-12 flex-shrink-0 items-center justify-center"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
          >
            <VariantThumb
              url={product.primaryMediaUrl}
              alt={product.name}
              previewCaption={product.sku}
              size="md"
            />
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
        width: 220,
        flexGrow: true,
        sortable: true,
        sortValue: (product) => product.name,
        cell: (product) => (
          <span className="block truncate text-text" title={product.name}>
            {product.name}
          </span>
        ),
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
      {
        id: 'weight',
        header: 'Weight',
        width: 88,
        align: 'right',
        defaultHidden: true,
        sortable: true,
        sortValue: (product) => product.weight ?? 0,
        cell: (product) => (
          <span className="font-mono tabular-nums text-text-muted">
            {product.weight != null ? `${product.weight} ${product.weightUnit ?? ''}`.trim() : '—'}
          </span>
        ),
      },
      {
        id: 'dims',
        header: 'L×W×H',
        width: 120,
        defaultHidden: true,
        cell: (product) => (
          <span className="font-mono text-xs text-text-muted">
            {product.length != null && product.width != null && product.height != null
              ? `${product.length}×${product.width}×${product.height} ${product.dimUnit ?? ''}`.trim()
              : '—'}
          </span>
        ),
      },
      {
        id: 'hsTariffCode',
        header: 'HS code',
        width: 100,
        defaultHidden: true,
        cell: (product) => (
          <span className="font-mono text-xs text-text-muted">{product.hsTariffCode ?? '—'}</span>
        ),
      },
      {
        id: 'countryOfOrigin',
        header: 'Origin',
        width: 72,
        defaultHidden: true,
        cell: (product) => (
          <span className="font-mono text-xs text-text-muted">{product.countryOfOrigin ?? '—'}</span>
        ),
      },
      {
        id: 'isHazmat',
        header: 'Hazmat',
        width: 72,
        align: 'center',
        defaultHidden: true,
        cell: (product) => (
          <span className="text-xs text-text-muted">{product.isHazmat ? 'Yes' : 'No'}</span>
        ),
      },
      {
        id: 'palletTiHi',
        header: 'Ti×Hi',
        width: 72,
        align: 'center',
        defaultHidden: true,
        cell: (product) => (
          <span className="font-mono text-xs text-text-muted">
            {product.palletTie != null && product.palletHigh != null
              ? `${product.palletTie}×${product.palletHigh}`
              : '—'}
          </span>
        ),
      },
      {
        id: 'storageTempZone',
        header: 'Temp',
        width: 100,
        defaultHidden: true,
        cell: (product) => (
          <span className="text-xs text-text-muted">{product.storageTempZone ?? '—'}</span>
        ),
      },
      {
        id: 'isFragile',
        header: 'Fragile',
        width: 72,
        align: 'center',
        defaultHidden: true,
        cell: (product) => (
          <span className="text-xs text-text-muted">{product.isFragile ? 'Yes' : 'No'}</span>
        ),
      },
      {
        id: 'abcClassification',
        header: 'ABC',
        width: 56,
        align: 'center',
        defaultHidden: true,
        cell: (product) => (
          <span className="font-mono text-xs text-text-muted">{product.abcClassification ?? '—'}</span>
        ),
      },
      {
        id: 'lifecycleStatus',
        header: 'Lifecycle',
        width: 110,
        defaultHidden: true,
        cell: (product) => (
          <span className="text-xs text-text-muted">{product.lifecycleStatus ?? '—'}</span>
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
                search={search}
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
  }, [canManage, reorderMutation.mutateAsync, search, syncSupported]);

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
        <DataListToolbar columnItems={columnItems} gridId="products">
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

      <div className="flex min-h-0 w-full flex-1 flex-col overflow-hidden">
        <VirtualizedTable
          gridId="products"
          columns={columns}
          rows={displayed}
          getRowId={(row) => row.id}
          selectedRowId={peekProductId}
          onRowClick={(row) => {
            setPeekTab('details');
            setPeekProductId(row.id);
          }}
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
      </div>

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
          <div className="space-y-4">
            <div className="flex flex-wrap gap-2" role="tablist" aria-label="Product peek tabs">
              <Button
                type="button"
                size="sm"
                role="tab"
                aria-selected={peekTab === 'details'}
                variant={peekTab === 'details' ? 'primary' : 'secondary'}
                data-testid="product-peek-tab-details"
                onClick={() => setPeekTab('details')}
              >
                Details
              </Button>
              <Button
                type="button"
                size="sm"
                role="tab"
                aria-selected={peekTab === 'ledger'}
                variant={peekTab === 'ledger' ? 'primary' : 'secondary'}
                data-testid="product-peek-tab-ledger"
                onClick={() => setPeekTab('ledger')}
              >
                Ledger History
              </Button>
            </div>

            {peekTab === 'details' ? (
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
              <LedgerHistoryTable variantId={peekProduct.id} limit={50} embedded />
            )}
          </div>
        ) : (
          <p className="text-sm text-text-muted">Loading…</p>
        )}
      </RightPeekDrawer>
    </div>
  );
}
