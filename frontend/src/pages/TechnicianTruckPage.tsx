import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ScanLine, Truck } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { TenantLocation, VanStockLevel, VehicleAssignment } from '@/api/types';
import { useHardwareScanner } from '@/hooks/useHardwareScanner';
import { useScanFeedback } from '@/hooks/useScanFeedback';
import { useScanBufferStore } from '@/stores/scanBuffer';
import { useSessionStore } from '@/stores/session';
import { BigButton } from '@/components/ui/BigButton';
import { ScanFlashOverlay } from '@/components/ui/ScanFlashOverlay';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';

export function TechnicianTruckPage() {
  const queryClient = useQueryClient();
  const lastScan = useScanBufferStore((s) => s.lastScan);
  const { flash, triggerSuccess, triggerError } = useScanFeedback();
  const userId = useSessionStore((s) => s.user?.id);
  const hasRole = useSessionStore((s) => s.hasRole);
  const canAssign = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const canReplenish = canAssign;

  const [consumeQty, setConsumeQty] = useState('1');
  const [selectedVariantId, setSelectedVariantId] = useState<string | null>(null);
  const [replenishVariantId, setReplenishVariantId] = useState('');
  const [replenishQty, setReplenishQty] = useState('1');
  const [fromWarehouseId, setFromWarehouseId] = useState('');
  const [assignVehicleId, setAssignVehicleId] = useState('');

  const { data: assignment, isLoading: assignmentLoading } = useQuery({
    queryKey: ['field-vehicle-active'],
    queryFn: async () => {
      const res = await apiClient.get<VehicleAssignment>('/api/v1/field/vehicles/active', {
        validateStatus: (status) => status === 200 || status === 204,
      });
      return res.status === 204 ? null : res.data;
    },
    retry: false,
  });

  // Van stock requires a VEHICLE location — never fall back to the office warehouse context.
  const locationId = assignment?.locationId ?? null;

  const { data: vehicles = [] } = useQuery({
    queryKey: ['field-vehicles'],
    enabled: !assignment && canAssign,
    queryFn: async () =>
      (await apiClient.get<TenantLocation[]>('/api/v1/locations', { params: { type: 'VEHICLE' } })).data,
  });

  const { data: stock = [], isLoading } = useQuery({
    queryKey: ['field-van-stock', locationId],
    enabled: !!locationId,
    queryFn: async () =>
      (await apiClient.get<VanStockLevel[]>('/api/v1/field/van/stock', {
        params: { locationId },
      })).data,
  });

  const assignMutation = useMutation({
    mutationFn: async (vehicleLocationId: string) => {
      if (!userId) throw new Error('Not signed in');
      await apiClient.post('/api/v1/field/vehicles/assign', {
        locationId: vehicleLocationId,
        technicianUserId: userId,
      });
    },
    onSuccess: () => {
      triggerSuccess();
      void queryClient.invalidateQueries({ queryKey: ['field-vehicle-active'] });
      void queryClient.invalidateQueries({ queryKey: ['field-van-stock'] });
    },
    onError: () => triggerError(),
  });

  const consumeMutation = useMutation({
    mutationFn: async ({ variantId, quantity }: { variantId: string; quantity: number }) => {
      await apiClient.post('/api/v1/field/van/consume', {
        variantId,
        quantity,
        reason: 'SERVICE_CONSUMPTION',
      });
    },
    onSuccess: () => {
      triggerSuccess();
      void queryClient.invalidateQueries({ queryKey: ['field-van-stock'] });
    },
    onError: () => triggerError(),
  });

  const replenishMutation = useMutation({
    mutationFn: async () => {
      if (!locationId || !fromWarehouseId || !replenishVariantId) {
        throw new Error('Missing replenish fields');
      }
      await apiClient.post('/api/v1/field/van/replenish', {
        fromWarehouseId,
        toVehicleLocationId: locationId,
        items: [{ variantId: replenishVariantId, quantity: Number(replenishQty) }],
      });
    },
    onSuccess: () => {
      triggerSuccess();
      void queryClient.invalidateQueries({ queryKey: ['field-van-stock'] });
    },
    onError: () => triggerError(),
  });

  const skuIndex = useMemo(() => {
    const map = new Map<string, VanStockLevel>();
    for (const row of stock) {
      if (row.sku) map.set(row.sku.toLowerCase(), row);
    }
    return map;
  }, [stock]);

  useHardwareScanner({
    enabled: !!locationId,
    captureAll: true,
    onScan: (code) => {
      if (!code.length) return;
      const row = skuIndex.get(code.toLowerCase());
      if (row) {
        triggerSuccess();
        setSelectedVariantId(row.variantId);
        consumeMutation.mutate({
          variantId: row.variantId,
          quantity: Number(consumeQty) || 1,
        });
      } else {
        triggerError();
      }
    },
  });

  return (
    <div className="flex min-h-full flex-col p-4 pb-8" data-theme="warehouse">
      <ScanFlashOverlay flash={flash} />

      <div className="mb-6 text-center">
        <div className="mb-2 flex items-center justify-center gap-2">
          <Truck className="h-6 w-6 text-accent" />
          <h1 className="text-2xl font-bold text-text">Technician Truck</h1>
        </div>
        <p className="text-sm text-text-muted">
          {assignment
            ? `${assignment.locationName ?? assignment.locationCode ?? 'Van'} stock`
            : 'No vehicle assigned'}
        </p>
      </div>

      {!assignmentLoading && !assignment && (
        <Card className="mb-6 space-y-3" padding="md">
          <p className="text-sm text-text-muted">
            Assign a service van before viewing stock or consuming parts.
          </p>
          {canAssign ? (
            <>
              <Select
                label="Vehicle"
                value={assignVehicleId}
                onChange={(e) => setAssignVehicleId(e.target.value)}
              >
                <option value="">Select vehicle…</option>
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.name} ({v.code})
                  </option>
                ))}
              </Select>
              {vehicles.length === 0 && (
                <p className="text-xs text-text-muted">
                  No VEHICLE locations yet. Create one under Settings → Locations.
                </p>
              )}
              <BigButton
                variant="primary"
                disabled={!assignVehicleId || assignMutation.isPending}
                onClick={() => assignMutation.mutate(assignVehicleId)}
              >
                Assign to me
              </BigButton>
            </>
          ) : (
            <p className="text-sm text-text-muted">
              Ask a warehouse manager to assign a vehicle to your account.
            </p>
          )}
        </Card>
      )}

      {locationId && (
        <>
          <Card className="mb-6 text-center" padding="lg">
            <ScanLine className="mx-auto mb-3 h-10 w-10 text-accent" />
            <p className="text-sm text-text-muted">Scan SKU to consume</p>
            <p className="mt-1 font-mono text-2xl font-bold text-text">{lastScan ?? 'Ready'}</p>
            <div className="mx-auto mt-4 max-w-[8rem]">
              <Input
                type="number"
                min={1}
                value={consumeQty}
                onChange={(e) => setConsumeQty(e.target.value)}
                aria-label="Consume quantity"
              />
            </div>
          </Card>

          <div className="mb-6 space-y-3">
            <p className="text-sm font-medium text-text-muted">Van stock</p>
            {isLoading && <p className="text-sm text-text-muted">Loading…</p>}
            {!isLoading && stock.length === 0 && (
              <p className="text-sm text-text-muted">No stock on this vehicle</p>
            )}
            {stock.map((row) => (
              <button
                key={`${row.variantId}-${row.lotId ?? 'none'}`}
                type="button"
                onClick={() => setSelectedVariantId(row.variantId)}
                className={`w-full rounded-xl border p-4 text-left ${
                  selectedVariantId === row.variantId
                    ? 'border-accent bg-accent-muted'
                    : 'border-border bg-surface-raised'
                }`}
              >
                <p className="font-mono text-base font-bold text-text">{row.sku ?? row.variantId}</p>
                <p className="text-sm text-text-muted">
                  On hand {row.onHand} · available {row.available}
                </p>
              </button>
            ))}
          </div>

          {selectedVariantId && (
            <BigButton
              className="mb-4"
              variant="danger"
              disabled={consumeMutation.isPending}
              onClick={() =>
                consumeMutation.mutate({
                  variantId: selectedVariantId,
                  quantity: Number(consumeQty) || 1,
                })
              }
            >
              Consume from van
            </BigButton>
          )}

          {canReplenish && (
            <Card className="mt-auto space-y-3" padding="md">
              <p className="text-sm font-semibold text-text">Replenish van</p>
              <Input
                placeholder="From warehouse ID"
                value={fromWarehouseId}
                onChange={(e) => setFromWarehouseId(e.target.value)}
              />
              <Input
                placeholder="Variant ID"
                value={replenishVariantId}
                onChange={(e) => setReplenishVariantId(e.target.value)}
              />
              <Input
                type="number"
                min={1}
                value={replenishQty}
                onChange={(e) => setReplenishQty(e.target.value)}
                aria-label="Replenish quantity"
              />
              <BigButton
                variant="primary"
                disabled={replenishMutation.isPending || !fromWarehouseId || !replenishVariantId}
                onClick={() => replenishMutation.mutate()}
              >
                Transfer to van
              </BigButton>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
