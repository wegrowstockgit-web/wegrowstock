import { EnterpriseRouteGate } from '@/components/auth/EnterpriseRouteGate';
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
        <EnterpriseRouteGate requiredModule="MESH_NETWORK" roles={['OWNER', 'ADMIN']}>
          <MeshNetworkPage />
        </EnterpriseRouteGate>
      ),
    },
  ],
  navItems: [{ to: '/mesh-network', label: 'Mesh Network', moduleId: 'mesh' }],
});
