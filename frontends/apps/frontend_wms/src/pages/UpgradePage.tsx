import { Sparkles } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';

/**
 * Commercial-tier denial: the tenant has not purchased this module.
 */
export function UpgradePage() {
  const navigate = useNavigate();
  return (
    <div
      data-testid="upgrade-page"
      className="flex min-h-[50dvh] flex-col items-center justify-center gap-5 px-4 py-12 text-center sm:min-h-[70dvh] sm:px-8"
    >
      <div className="rounded-full bg-accent-muted p-4">
        <Sparkles className="h-9 w-9 text-accent" aria-hidden />
      </div>
      <div className="max-w-md space-y-2">
        <p className="text-sm font-medium uppercase tracking-wide text-text-muted">Upgrade required</p>
        <h1 className="text-balance text-2xl font-semibold text-text sm:text-3xl">
          This module is not on your plan
        </h1>
        <p className="text-pretty text-sm text-text-muted">
          Your workspace has not purchased this commercial module. Ask an owner to enable it, or
          return to the dashboard.
        </p>
      </div>
      <Button type="button" className="min-h-11" data-testid="upgrade-home" onClick={() => navigate('/dashboard')}>
        Return to Dashboard
      </Button>
    </div>
  );
}
