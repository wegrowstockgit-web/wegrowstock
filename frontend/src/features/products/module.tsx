import { ProductsPage } from '@/features/products/ProductsPage';
import { defineModule, isModuleBuildEnabled } from '@/lib/router/moduleRegistry';

export const productsModule = defineModule({
  id: 'products',
  enabled: isModuleBuildEnabled('VITE_ENABLE_PRODUCTS'),
  officeRoutes: [{ path: 'products', element: <ProductsPage /> }],
  navItems: [{ to: '/products', label: 'Products', moduleId: 'products' }],
});
