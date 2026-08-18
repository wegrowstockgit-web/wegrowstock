import { ShieldOff } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { isExclusiveRole, useSessionRoles } from '@/stores/session';

/**
 * Security denial: the signed-in user lacks the required permission.
 */
export function UnauthorizedPage() {
  const navigate = useNavigate();
  const roles = useSessionRoles();
  const home = isExclusiveRole(roles, 'PICKER') ? '/fulfillment' : '/dashboard';

  return (
    <div
      data-testid="unauthorized-page"
      className="flex min-h-[50dvh] flex-col items-center justify-center gap-5 px-4 py-12 text-center sm:min-h-[70dvh] sm:px-8"
    >
      <div className="rounded-full bg-danger/10 p-4">
        <ShieldOff className="h-9 w-9 text-danger" aria-hidden />
      </div>
      <div className="max-w-md space-y-2">
        <p className="text-sm font-medium uppercase tracking-wide text-text-muted">Unauthorized</p>
        <h1 className="text-balance text-2xl font-semibold text-text sm:text-3xl">
          You do not have access
        </h1>
        <p className="text-pretty text-sm text-text-muted">
          Your role does not include the permission required for this page. Ask an administrator
          to grant access, or return to a page you can use.
        </p>
      </div>
      <Button type="button" className="min-h-11" data-testid="unauthorized-home" onClick={() => navigate(home)}>
        Go back
      </Button>
    </div>
  );
}
