import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { useToast } from '@/components/ui/Toast';
import { extractApiError } from '@/lib/apiClient';
import { AddChildInline } from '@/features/settings/WarehouseAddChild';
import { CHILD_TYPE, formatLocationType } from '@/features/settings/warehouseTree';

export function WarehouseLocationDrawer({
  location,
  onClose,
}: {
  location: TenantLocation | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [maxWeightKg, setMaxWeightKg] = useState('');
  const [lengthCm, setLengthCm] = useState('');
  const [widthCm, setWidthCm] = useState('');
  const [heightCm, setHeightCm] = useState('');
  const [pickSequence, setPickSequence] = useState('');
  const [zoneBehavior, setZoneBehavior] = useState('STANDARD');
  const [addingChild, setAddingChild] = useState(false);

  useEffect(() => {
    if (!location) return;
    setName(location.name ?? '');
    setCode(location.code ?? '');
    setMaxWeightKg(location.maxWeightKg != null ? String(location.maxWeightKg) : '');
    setLengthCm('');
    setWidthCm('');
    setHeightCm('');
    setPickSequence(location.sequenceIndex != null ? String(location.sequenceIndex) : '0');
    setZoneBehavior(location.zoneBehavior ?? 'STANDARD');
    setAddingChild(false);
  }, [location]);

  const save = useMutation({
    mutationFn: async () => {
      if (!location) return;
      await apiClient.patch(`/api/v1/locations/${location.id}`, {
        name: name.trim(),
        code: code.trim().toUpperCase(),
        sequenceIndex: pickSequence === '' ? 0 : Number(pickSequence),
        maxWeightKg: maxWeightKg === '' ? null : Number(maxWeightKg),
        lengthCm: lengthCm === '' ? null : Number(lengthCm),
        widthCm: widthCm === '' ? null : Number(widthCm),
        heightCm: heightCm === '' ? null : Number(heightCm),
        zoneBehavior,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locations'] });
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      toast('Location updated.', { tone: 'success' });
      onClose();
    },
    onError: (error) => {
      toast(extractApiError(error, 'Could not save location settings.'), { tone: 'danger' });
    },
  });

  const childType = location ? CHILD_TYPE[location.type] : null;

  return (
    <RightPeekDrawer
      open={location != null}
      onClose={onClose}
      title={location ? `${location.name}` : 'Location'}
      description={location ? `${formatLocationType(location.type)} · ${location.path}` : undefined}
    >
      {location && (
        <form
          className="space-y-4"
          data-testid="warehouse-location-form"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <Input
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            data-testid="location-edit-name"
          />
          <Input
            label="Type"
            value={formatLocationType(location.type)}
            readOnly
            data-testid="location-edit-type"
          />
          <Input
            label="Barcode / code"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            required
            data-testid="location-edit-code"
          />
          <Input
            label="Max weight (kg)"
            type="number"
            min="0"
            step="0.01"
            value={maxWeightKg}
            onChange={(e) => setMaxWeightKg(e.target.value)}
            data-testid="location-edit-max-weight"
          />
          <div className="grid grid-cols-3 gap-2">
            <Input
              label="Length (cm)"
              type="number"
              min="0"
              step="0.1"
              value={lengthCm}
              onChange={(e) => setLengthCm(e.target.value)}
              data-testid="location-edit-length"
            />
            <Input
              label="Width (cm)"
              type="number"
              min="0"
              step="0.1"
              value={widthCm}
              onChange={(e) => setWidthCm(e.target.value)}
              data-testid="location-edit-width"
            />
            <Input
              label="Height (cm)"
              type="number"
              min="0"
              step="0.1"
              value={heightCm}
              onChange={(e) => setHeightCm(e.target.value)}
              data-testid="location-edit-height"
            />
          </div>
          <Input
            label="Pick sequence"
            type="number"
            min="0"
            step="1"
            value={pickSequence}
            onChange={(e) => setPickSequence(e.target.value)}
            data-testid="location-edit-pick-sequence"
          />
          <label className="flex flex-col gap-1.5 text-sm font-medium text-text">
            Status
            <select
              className="h-10 rounded-md border border-border bg-surface-raised px-3 text-sm"
              value={zoneBehavior}
              onChange={(e) => setZoneBehavior(e.target.value)}
              data-testid="location-edit-status"
            >
              <option value="STANDARD">Active</option>
              <option value="PICK_FACE">Pick face</option>
              <option value="RESERVE">Reserve</option>
              <option value="RECEIVING">Receiving</option>
            </select>
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={save.isPending} data-testid="location-edit-save">
              Save location
            </Button>
          </div>
        </form>
      )}

      {location && childType && (
        <div className="mt-6 border-t border-border pt-4">
          {addingChild ? (
            <AddChildInline parent={location} onDone={() => setAddingChild(false)} />
          ) : (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => setAddingChild(true)}
              data-testid="location-add-child"
            >
              Add {String(childType).toLowerCase()}
            </Button>
          )}
        </div>
      )}
    </RightPeekDrawer>
  );
}
