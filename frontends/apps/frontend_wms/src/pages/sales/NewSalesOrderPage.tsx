import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { InventoryLevel, ProductVariant, TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { CustomerSearchCombobox } from '@/features/sales/CustomerSearchCombobox';
import { SkuSearchCombobox } from '@/features/purchasing/SkuSearchCombobox';
import { formatCurrency } from '@/lib/utils';

interface DraftLine {
  key: string;
  variantId: string;
  sku: string;
  name: string;
  listPrice: number;
  qtyOrdered: string;
  unitPrice: string;
}

interface CustomerSalesContext {
  customerId: string;
  name: string;
  creditLimit: number;
  availableCredit: number;
  priceTierName: string;
  priceTierDiscountPercent: number;
  paymentTerms?: string | null;
}

function nextKey() {
  return crypto.randomUUID();
}

function money(value: string | number | undefined) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function stockAtWarehouse(
  levels: InventoryLevel[],
  locations: TenantLocation[],
  warehouseId?: string,
) {
  const allowed = new Set<string>();
  if (warehouseId) {
    const warehouse = locations.find((loc) => loc.id === warehouseId);
    for (const loc of locations) {
      if (
        loc.id === warehouseId ||
        (warehouse?.path && loc.path && loc.path.startsWith(warehouse.path))
      ) {
        allowed.add(loc.id);
      }
    }
  }
  return levels.reduce(
    (acc, row) => {
      if (warehouseId && allowed.size > 0 && !allowed.has(row.locationId)) {
        return acc;
      }
      const onHand = Number(row.onHand ?? 0);
      const available =
        row.available != null ? Number(row.available) : onHand - Number(row.allocated ?? 0);
      return { onHand: acc.onHand + onHand, available: acc.available + available };
    },
    { onHand: 0, available: 0 },
  );
}

function LineAtp({
  variantId,
  warehouseId,
}: {
  variantId: string;
  warehouseId?: string;
}) {
  const { data: locations = [] } = useQuery({
    queryKey: ['locations'],
    queryFn: async () => (await apiClient.get<TenantLocation[]>('/api/v1/locations')).data,
    enabled: !!variantId,
  });
  const { data: levels = [] } = useQuery({
    queryKey: ['inventory-levels', variantId],
    queryFn: async () =>
      (await apiClient.get<InventoryLevel[]>(`/api/v1/inventory/levels`, { params: { variantId } }))
        .data,
    enabled: !!variantId,
  });
  if (!variantId) return <span className="text-xs text-text-muted">Select a SKU</span>;
  const stock = stockAtWarehouse(levels, locations, warehouseId);
  return (
    <span className="text-xs text-text-muted" data-testid={`so-atp-${variantId}`}>
      On-Hand: {stock.onHand} | Available: {stock.available}
    </span>
  );
}

export function NewSalesOrderPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [customerId, setCustomerId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [sourceLocationId, setSourceLocationId] = useState('');
  const [customerPoNumber, setCustomerPoNumber] = useState('');
  const [requestedShipDate, setRequestedShipDate] = useState('');
  const [lines, setLines] = useState<DraftLine[]>([
    { key: nextKey(), variantId: '', sku: '', name: '', listPrice: 0, qtyOrdered: '1', unitPrice: '' },
  ]);
  const [error, setError] = useState('');

  const { data: warehouses = [] } = useQuery({
    queryKey: ['locations', 'warehouse'],
    queryFn: async () =>
      (await apiClient.get<TenantLocation[]>('/api/v1/locations', { params: { type: 'WAREHOUSE' } }))
        .data,
  });

  const { data: salesContext } = useQuery({
    queryKey: ['customers', customerId, 'sales-context'],
    queryFn: async () =>
      (await apiClient.get<CustomerSalesContext>(`/api/v1/customers/${customerId}/sales-context`))
        .data,
    enabled: !!customerId,
  });

  const discountPct = Number(salesContext?.priceTierDiscountPercent ?? 0);

  const updateLine = (key: string, patch: Partial<DraftLine>) => {
    setLines((prev) => prev.map((line) => (line.key === key ? { ...line, ...patch } : line)));
  };

  const applyVariant = (key: string, variant: ProductVariant) => {
    const list = Number(variant.price ?? 0);
    const unit = list * (1 - discountPct / 100);
    updateLine(key, {
      variantId: variant.id,
      sku: variant.sku,
      name: variant.name,
      listPrice: list,
      unitPrice: unit ? unit.toFixed(2) : '',
    });
  };

  const validLines = lines.filter((line) => line.variantId && money(line.qtyOrdered) > 0);
  const subtotal = validLines.reduce(
    (sum, line) => sum + money(line.qtyOrdered) * money(line.unitPrice),
    0,
  );
  const estimatedTax = 0;
  const estimatedShipping = 0;
  const grandTotal = subtotal + estimatedTax + estimatedShipping;
  const creditWarn =
    !!salesContext &&
    Number(salesContext.availableCredit) > 0 &&
    grandTotal > Number(salesContext.availableCredit);

  const mutation = useMutation({
    mutationFn: async () => {
      const created = await apiClient.post<{ id: string; number: string }>('/api/v1/sales-orders', {
        customerId,
        number: `SO-${Date.now()}`,
        channel: 'DIRECT',
        sourceLocationId: sourceLocationId || undefined,
        customerPoNumber: customerPoNumber || undefined,
        requestedShipDate: requestedShipDate ? new Date(requestedShipDate).toISOString() : undefined,
        lines: validLines.map((line) => ({
          variantId: line.variantId,
          qtyOrdered: money(line.qtyOrdered),
          unitPrice: line.unitPrice ? money(line.unitPrice) : undefined,
        })),
      });
      return created.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['sales-orders'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      navigate('/sales-orders');
    },
    onError: () => setError('Could not create the order. Check the fields and try again.'),
  });

  const selectedWarehouse = useMemo(
    () => warehouses.find((w) => w.id === sourceLocationId),
    [sourceLocationId, warehouses],
  );

  return (
    <div className="flex h-full min-h-0 flex-col" data-testid="new-sales-order-page">
      <div className="flex shrink-0 items-center justify-between gap-4 border-b border-border/60 px-6 py-4">
        <div>
          <button
            type="button"
            className="mb-1 flex items-center gap-1 text-sm text-text-muted hover:text-text"
            onClick={() => navigate('/sales-orders')}
          >
            <ArrowLeft className="h-4 w-4" />
            Sales orders
          </button>
          <h1 className="text-2xl font-bold text-text">New sales order</h1>
          <p className="mt-1 text-sm text-text-muted">
            Search customers and SKUs, review live ATP, then create the weGrowStock draft.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => navigate('/sales-orders')}>
            Cancel
          </Button>
          <Button
            data-testid="so-create-order"
            loading={mutation.isPending}
            disabled={!customerId || validLines.length === 0}
            onClick={() => {
              setError('');
              mutation.mutate();
            }}
          >
            Create order
          </Button>
        </div>
      </div>

      <div className="min-h-0 flex-1 space-y-6 overflow-auto p-6">
        <Card>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div>
              <CustomerSearchCombobox
                value={customerId}
                selectedLabel={customerName}
                onSelect={(customer) => {
                  setCustomerId(customer.id);
                  setCustomerName(customer.name);
                }}
              />
              {salesContext && (
                <p
                  className="mt-2 rounded-md bg-accent-muted/40 px-2.5 py-1.5 text-xs text-text"
                  data-testid="so-customer-credit"
                >
                  Available credit {formatCurrency(Number(salesContext.availableCredit))} · Price
                  tier {salesContext.priceTierName}
                  {salesContext.paymentTerms ? ` · ${salesContext.paymentTerms}` : ''}
                </p>
              )}
            </div>
            <Select
              label="Ship-from warehouse"
              data-testid="so-warehouse"
              value={sourceLocationId}
              onChange={(e) => setSourceLocationId(e.target.value)}
            >
              <option value="">All warehouses</option>
              {warehouses.map((warehouse) => (
                <option key={warehouse.id} value={warehouse.id}>
                  {warehouse.name}
                </option>
              ))}
            </Select>
            <Input
              label="Customer PO #"
              data-testid="so-customer-po"
              value={customerPoNumber}
              onChange={(e) => setCustomerPoNumber(e.target.value)}
              placeholder="Customer reference"
            />
            <Input
              label="Requested ship date"
              data-testid="so-ship-date"
              type="date"
              value={requestedShipDate}
              onChange={(e) => setRequestedShipDate(e.target.value)}
            />
          </div>
          {selectedWarehouse && (
            <p className="mt-3 text-xs text-text-muted">
              ATP is scoped to {selectedWarehouse.name} and its bins.
            </p>
          )}
        </Card>

        <Card>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-text">Line items</h2>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              data-testid="so-add-line"
              onClick={() =>
                setLines((prev) => [
                  ...prev,
                  {
                    key: nextKey(),
                    variantId: '',
                    sku: '',
                    name: '',
                    listPrice: 0,
                    qtyOrdered: '1',
                    unitPrice: '',
                  },
                ])
              }
            >
              <Plus className="h-4 w-4" />
              Add line
            </Button>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>SKU</TableHead>
                <TableHead>ATP</TableHead>
                <TableHead>Qty</TableHead>
                <TableHead>Unit price</TableHead>
                <TableHead align="right">Extended</TableHead>
                <TableHead align="right"> </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {lines.map((line) => {
                const extended = money(line.qtyOrdered) * money(line.unitPrice);
                return (
                  <TableRow key={line.key}>
                    <TableCell className="min-w-[16rem] align-top">
                      <SkuSearchCombobox
                        value={line.variantId}
                        selectedLabel={line.sku ? `${line.sku} — ${line.name}` : undefined}
                        onSelect={(variant) => applyVariant(line.key, variant)}
                        label="SKU"
                        testId={`so-add-sku-${line.key}`}
                        optionTestId="so-sku-option"
                      />
                    </TableCell>
                    <TableCell className="align-top pt-8">
                      <LineAtp variantId={line.variantId} warehouseId={sourceLocationId || undefined} />
                    </TableCell>
                    <TableCell className="align-top pt-6">
                      <Input
                        aria-label={`Quantity ${line.sku || 'line'}`}
                        data-testid={`so-qty-${line.key}`}
                        type="number"
                        min="1"
                        className="w-24"
                        value={line.qtyOrdered}
                        onChange={(e) => updateLine(line.key, { qtyOrdered: e.target.value })}
                      />
                    </TableCell>
                    <TableCell className="align-top pt-6">
                      <Input
                        aria-label={`Unit price ${line.sku || 'line'}`}
                        data-testid={`so-price-${line.key}`}
                        type="number"
                        min="0"
                        step="0.01"
                        className="w-28"
                        value={line.unitPrice}
                        onChange={(e) => updateLine(line.key, { unitPrice: e.target.value })}
                      />
                    </TableCell>
                    <TableCell align="right" className="align-top pt-8 font-mono">
                      <span data-testid={`so-extended-${line.key}`}>{formatCurrency(extended)}</span>
                    </TableCell>
                    <TableCell align="right" className="align-top pt-6">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label="Remove line"
                        data-testid={`so-remove-line-${line.key}`}
                        onClick={() =>
                          setLines((prev) =>
                            prev.length === 1
                              ? [
                                  {
                                    key: nextKey(),
                                    variantId: '',
                                    sku: '',
                                    name: '',
                                    listPrice: 0,
                                    qtyOrdered: '1',
                                    unitPrice: '',
                                  },
                                ]
                              : prev.filter((row) => row.key !== line.key),
                          )
                        }
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Card>
      </div>

      <div className="shrink-0 border-t border-border bg-surface-raised px-6 py-4">
        {creditWarn && (
          <p className="mb-3 text-sm text-warning" data-testid="so-credit-warning">
            Grand total exceeds available credit. Confirm with finance before submitting a large
            order.
          </p>
        )}
        {error && <p className="mb-3 text-sm text-danger">{error}</p>}
        <div className="ml-auto grid max-w-sm grid-cols-2 gap-x-6 gap-y-1 text-sm">
          <span className="text-text-muted">Subtotal</span>
          <span className="text-right font-mono" data-testid="so-subtotal">
            {formatCurrency(subtotal)}
          </span>
          <span className="text-text-muted">Estimated tax</span>
          <span className="text-right font-mono">{formatCurrency(estimatedTax)}</span>
          <span className="text-text-muted">Estimated shipping</span>
          <span className="text-right font-mono">{formatCurrency(estimatedShipping)}</span>
          <span className="font-semibold text-text">Grand total</span>
          <span className="text-right font-mono font-semibold" data-testid="so-grand-total">
            {formatCurrency(grandTotal)}
          </span>
        </div>
      </div>
    </div>
  );
}
