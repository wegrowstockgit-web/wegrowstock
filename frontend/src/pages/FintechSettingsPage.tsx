import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { FinancingCockpit } from '@/components/fintech/FinancingCockpit';

/**
 * Surface A — capital underwriting, draw balances, factoring (separated from general Settings).
 */
export function FintechSettingsPage() {
  return (
    <div className="space-y-6 p-6" data-testid="fintech-settings-page">
      <div>
        <Link
          to="/settings"
          className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-text-muted hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          All settings
        </Link>
        <h1 className="text-2xl font-bold text-text">Cash flow & financing</h1>
        <p className="mt-1 text-sm text-text-muted">
          Underwriting panel, capital availability, and ledger draw balances
        </p>
      </div>
      <FinancingCockpit />
    </div>
  );
}
