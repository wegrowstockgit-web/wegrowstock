import { useNavigate } from 'react-router-dom';
import { ReplenishmentQueue } from '@/features/fulfillment/ReplenishmentQueue';

/**
 * Floor route for reserve → pick-face replenishment tasks.
 * Uses the warehouse shell (no officeOnly) so mobile scanners stay on Surface B.
 */
export function ReplenishmentsPage() {
  const navigate = useNavigate();

  return (
    <div className="mx-auto min-h-0 w-full max-w-3xl p-4 sm:p-6" data-testid="replenishments-page">
      <ReplenishmentQueue onClose={() => navigate('/fulfillment')} />
    </div>
  );
}
