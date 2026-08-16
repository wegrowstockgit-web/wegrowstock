import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { MeshNetworkPage } from '@/features/mesh/MeshNetworkPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const meshModule = defineModule({
  id: 'mesh',
  enabled: isModuleBuildEnabled('VITE_ENABLE_MESH'),
  commercialModule: 'MESH_NETWORK',
  officeRoutes: [
    {
      path: 'mesh-network',
      element: (
        <ProtectedRoute roles={['OWNER', 'ADMIN']}>
          <MeshNetworkPage />
        </ProtectedRoute>
      ),
    },
  ],
  navItems: [{ to: '/mesh-network', label: 'Mesh Network', moduleId: 'mesh' }],
});
