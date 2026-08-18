import { EnterpriseRouteGate } from '@/components/auth/EnterpriseRouteGate';
import { FintechSettingsPage } from '@/features/fintech/FintechSettingsPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const fintechModule = defineModule({
  id: 'fintech',
  enabled: isModuleBuildEnabled('VITE_ENABLE_FINTECH'),
  commercialModule: 'FINTECH',
  officeRoutes: [
    {
      path: 'settings/fintech',
      element: (
        <EnterpriseRouteGate requiredModule="FINTECH" roles={['OWNER']} officeOnly>
          <FintechSettingsPage />
        </EnterpriseRouteGate>
      ),
    },
  ],
  navItems: [{ to: '/settings/fintech', label: 'Fintech', moduleId: 'fintech' }],
});
