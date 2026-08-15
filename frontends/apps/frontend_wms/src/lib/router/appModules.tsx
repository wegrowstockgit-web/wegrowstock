import { fintechModule } from '@/features/fintech/module';
import { fulfillmentModule } from '@/features/fulfillment/module';
import { productsModule } from '@/features/products/module';
import { purchasingModule } from '@/features/purchasing/module';
import { salesModule } from '@/features/sales/module';
import { registerAppModules, type AppModule } from '@/lib/router/moduleRegistry';

/**
 * Canonical feature module list. Omit or set `enabled: false` (via VITE_ENABLE_*)
 * to drop routes + nav without crashing the shell.
 */
export const APP_MODULES: AppModule[] = [
  productsModule,
  purchasingModule,
  salesModule,
  fulfillmentModule,
  fintechModule,
];

registerAppModules(APP_MODULES);

export { APP_MODULES as activeAppModules };
