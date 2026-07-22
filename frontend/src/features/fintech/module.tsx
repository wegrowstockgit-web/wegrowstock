import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { FintechSettingsPage } from '@/features/fintech/FintechSettingsPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const fintechModule = defineModule({
  id: 'fintech',
  enabled: isModuleBuildEnabled('VITE_ENABLE_FINTECH'),
  officeRoutes: [
    {
      path: 'settings/fintech',
      element: (
        <ProtectedRoute roles={['OWNER']} officeOnly>
          <FintechSettingsPage />
        </ProtectedRoute>
      ),
    },
  ],
  navItems: [{ to: '/settings/fintech', label: 'Fintech', moduleId: 'fintech' }],
});
