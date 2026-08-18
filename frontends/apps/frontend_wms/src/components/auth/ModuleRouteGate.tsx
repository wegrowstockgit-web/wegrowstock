import type { ReactNode } from 'react';
import { EnterpriseRouteGate } from './EnterpriseRouteGate';

export type ModuleRouteGateProps = {
  required?: string;
  anyOf?: readonly string[];
  children?: ReactNode;
};

/** @deprecated Prefer {@link EnterpriseRouteGate}. Kept as a thin commercial-only alias. */
export function ModuleRouteGate({ required, anyOf, children }: ModuleRouteGateProps) {
  return (
    <EnterpriseRouteGate requiredModule={required} anyOfModules={anyOf}>
      {children}
    </EnterpriseRouteGate>
  );
}
