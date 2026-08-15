import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { PlacesAddressInput, type GeocodedAddress } from './PlacesAddressInput';

export interface WarehouseFacilityFormProps {
  onSuccess?: () => void;
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
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [grossSqFt, setGrossSqFt] = useState('');
  const [officeSqFt, setOfficeSqFt] = useState('');
  const [clearHeight, setClearHeight] = useState('');
  const [dockDoors, setDockDoors] = useState('');
  const [weightLimit, setWeightLimit] = useState('');
  const [error, setError] = useState('');

  const applyGeocode = (addr: GeocodedAddress) => {
    setStreet(addr.street || street);
    setCity(addr.city || city);
    setState(addr.state || state);
    setPostalCode(addr.postalCode || postalCode);
    setCountry(addr.country || country);
    setLatitude(String(addr.latitude));
    setLongitude(String(addr.longitude));
  };

  const mutation = useMutation({
    mutationFn: async () => {
      if (!latitude || !longitude) {
        throw new Error('GEO_REQUIRED');
      }
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
        latitude: Number(latitude),
        longitude: Number(longitude),
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
      setLatitude('');
      setLongitude('');
      setGrossSqFt('');
      setOfficeSqFt('');
      setClearHeight('');
      setDockDoors('');
      setWeightLimit('');
      onSuccess?.();
    },
    onError: (err: Error) => {
      setError(
        err.message === 'GEO_REQUIRED'
          ? 'Geocode the address to capture latitude and longitude before saving.'
          : 'Could not create the warehouse. Check the fields and try again.',
      );
    },
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
        <PlacesAddressInput
          onResolved={applyGeocode}
          street={street}
          city={city}
          state={state}
          postalCode={postalCode}
          country={country}
        />
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
          <Input
            id="warehouse-latitude"
            name="latitude"
            label="Latitude"
            type="number"
            step="any"
            value={latitude}
            onChange={(e) => setLatitude(e.target.value)}
            required
            data-testid="warehouse-latitude"
          />
          <Input
            id="warehouse-longitude"
            name="longitude"
            label="Longitude"
            type="number"
            step="any"
            value={longitude}
            onChange={(e) => setLongitude(e.target.value)}
            required
            data-testid="warehouse-longitude"
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
