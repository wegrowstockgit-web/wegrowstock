import { useNavigate } from 'react-router-dom';
import { FileQuestion, Home } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useIsAuthenticated, useSessionRoles, isExclusiveRole } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';

function homePath(roles: readonly string[]): string {
  if (isExclusiveRole(roles, 'B2B_CUSTOMER')) return '/showroom/catalog';
  if (isExclusiveRole(roles, 'PICKER')) return '/fulfillment';
  return '/dashboard';
}

/**
 * Explicit 404 — replaces silent redirect-to-/ which confused operators.
 * Works inside AppShell or as a standalone catch-all.
 */
export function NotFoundPage() {
  const navigate = useNavigate();
  const authenticated = useIsAuthenticated();
  const roles = useSessionRoles();
  const warehouse = useActiveWarehouseStore((s) => s.warehouse);

  const destination = authenticated ? homePath(roles) : '/login';
  const ctaLabel = !authenticated
    ? 'Sign in'
    : isExclusiveRole(roles, 'PICKER')
      ? warehouse?.name
        ? `Return to ${warehouse.name}`
        : 'Return to floor ops'
      : isExclusiveRole(roles, 'B2B_CUSTOMER')
        ? 'Return to catalog'
        : 'Return to Dashboard';

  return (
    <div
      data-testid="not-found-page"
      className="flex min-h-[50dvh] flex-col items-center justify-center gap-5 px-4 py-12 text-center sm:min-h-[70dvh] sm:px-8"
    >
      <div className="rounded-full bg-accent-muted p-4">
        <FileQuestion className="h-9 w-9 text-accent" aria-hidden />
      </div>
      <div className="max-w-md space-y-2">
        <p className="text-sm font-medium uppercase tracking-wide text-text-muted">404</p>
        <h1 className="text-balance text-2xl font-semibold text-text sm:text-3xl">
          Page not found
        </h1>
        <p className="text-pretty text-sm text-text-muted">
          That URL is not part of this workspace. Check the address or head back to your home
          screen.
        </p>
      </div>
      <Button
        type="button"
        className="min-h-11"
        data-testid="not-found-home"
        onClick={() => navigate(destination)}
      >
        <Home className="h-4 w-4" />
        {ctaLabel}
      </Button>
    </div>
  );
}
