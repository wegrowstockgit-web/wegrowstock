import { useNavigate } from 'react-router-dom';
import { WarehouseFacilityForm } from '@/features/warehouses/WarehouseFacilityForm';

/**
 * Full-page facility create surface at `/warehouses/add` (office theme, max-w-7xl).
 */
export function AddWarehousePage() {
  const navigate = useNavigate();

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8" data-testid="warehouses-add-page">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Add warehouse</h1>
        <p className="mt-1 text-sm text-text-muted">
          Facility master data — logistics address, clear height, dock capacity, and floor load.
        </p>
      </div>
      <div className="max-w-3xl rounded-lg border border-border bg-surface p-6">
        <WarehouseFacilityForm
          onSuccess={() => navigate('/settings?tab=warehouses')}
          onCancel={() => navigate('/settings?tab=warehouses')}
        />
      </div>
    </div>
  );
}
