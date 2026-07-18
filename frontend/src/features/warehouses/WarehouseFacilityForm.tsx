import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export interface WarehouseFacilityFormProps {
  /** Called after a successful create. */
  onSuccess?: () => void;
  /** Optional cancel handler (modal). Omit on full-page route. */
  onCancel?: () => void;
  submitLabel?: string;
  autoFocus?: boolean;
}

/**
 * Shared facility master-data form used by Settings modal and `/warehouses/add`.
 */
export function WarehouseFacilityForm({
  onSuccess,
  onCancel,
  submitLabel = 'Add warehouse',
  autoFocus = true,
}: WarehouseFacilityFormProps) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [street, setStreet] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [country, setCountry] = useState('US');
  const [grossSqFt, setGrossSqFt] = useState('');
  const [officeSqFt, setOfficeSqFt] = useState('');
  const [clearHeight, setClearHeight] = useState('');
  const [dockDoors, setDockDoors] = useState('');
  const [weightLimit, setWeightLimit] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/locations', {
        type: 'WAREHOUSE',
        code,
        name,
        path: code,
        logisticsAddress: {
          street: street || undefined,
          city: city || undefined,
          state: state || undefined,
          postalCode: postalCode || undefined,
          country: country || undefined,
        },
        grossSquareFootage: grossSqFt ? Number(grossSqFt) : undefined,
        officeAreaSquareFootage: officeSqFt ? Number(officeSqFt) : undefined,
        clearHeightFeet: clearHeight ? Number(clearHeight) : undefined,
        totalDockDoors: dockDoors ? Number(dockDoors) : undefined,
        weightCapacityLimit: weightLimit ? Number(weightLimit) : undefined,
        floorLoadCapacityLbs: weightLimit ? Number(weightLimit) : undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locations'] });
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      setName('');
      setCode('');
      setStreet('');
      setCity('');
      setState('');
      setPostalCode('');
      setCountry('US');
      setGrossSqFt('');
      setOfficeSqFt('');
      setClearHeight('');
      setDockDoors('');
      setWeightLimit('');
      onSuccess?.();
    },
    onError: () => setError('Could not create the warehouse. Check the fields and try again.'),
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        setError('');
        mutation.mutate();
      }}
      className="space-y-4"
      data-testid="add-warehouse-form"
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          autoFocus={autoFocus}
        />
        <Input
          label="Code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="e.g. WH2"
          required
        />
      </div>
      <fieldset className="space-y-3 rounded-md border border-border p-3">
        <legend className="px-1 text-sm font-medium text-text">Logistics address</legend>
        <Input
          id="warehouse-logistics-street"
          name="logisticsStreet"
          label="Street"
          value={street}
          onChange={(e) => setStreet(e.target.value)}
        />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input
            id="warehouse-logistics-city"
            name="logisticsCity"
            label="City"
            value={city}
            onChange={(e) => setCity(e.target.value)}
          />
          <Input
            id="warehouse-logistics-state"
            name="logisticsState"
            label="State / Province"
            value={state}
            onChange={(e) => setState(e.target.value)}
          />
          <Input
            id="warehouse-logistics-postal"
            name="logisticsPostal"
            label="Postal code"
            value={postalCode}
            onChange={(e) => setPostalCode(e.target.value)}
          />
          <Input
            id="warehouse-logistics-country"
            name="logisticsCountry"
            label="Country"
            value={country}
            onChange={(e) => setCountry(e.target.value)}
          />
        </div>
      </fieldset>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Input
          label="Gross sq ft"
          type="number"
          min={0}
          value={grossSqFt}
          onChange={(e) => setGrossSqFt(e.target.value)}
        />
        <Input
          label="Office sq ft"
          type="number"
          min={0}
          value={officeSqFt}
          onChange={(e) => setOfficeSqFt(e.target.value)}
        />
        <Input
          label="Clear height (ft)"
          type="number"
          min={0}
          step="0.1"
          value={clearHeight}
          onChange={(e) => setClearHeight(e.target.value)}
        />
        <Input
          label="Dock doors"
          type="number"
          min={0}
          value={dockDoors}
          onChange={(e) => setDockDoors(e.target.value)}
        />
        <Input
          label="Floor load capacity (lbs)"
          type="number"
          min={0}
          value={weightLimit}
          onChange={(e) => setWeightLimit(e.target.value)}
          data-testid="warehouse-floor-load"
        />
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" loading={mutation.isPending} data-testid="add-warehouse-submit">
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
