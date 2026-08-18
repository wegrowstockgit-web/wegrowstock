import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useSessionStore } from '@/stores/session';
import { EnterpriseRouteGate } from './EnterpriseRouteGate';

function renderGate({
  path = '/manufacturing/boms',
  modules = ['CORE', 'MANUFACTURING'],
  roles = ['OWNER'],
  permissions = [] as string[],
  authenticated = true,
  gate,
}: {
  path?: string;
  modules?: string[];
  roles?: string[];
  permissions?: string[];
  authenticated?: boolean;
  gate: ReactElement;
}) {
  if (authenticated) {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles,
      tenantId: 't1',
      enabledModules: modules,
      grantedPermissions: permissions,
    });
  } else {
    useSessionStore.setState({
      authenticated: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  }

  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div data-testid="login">login</div>} />
        <Route path="/upgrade" element={<div data-testid="upgrade">upgrade</div>} />
        <Route path="/unauthorized" element={<div data-testid="unauthorized">unauthorized</div>} />
        <Route path="/dashboard" element={<div data-testid="dashboard">dashboard</div>} />
        <Route path="/fulfillment" element={<div data-testid="fulfillment">fulfillment</div>} />
        {gate}
      </Routes>
    </MemoryRouter>,
  );
}

describe('EnterpriseRouteGate', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: false,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('redirects anonymous users to /login', () => {
    renderGate({
      authenticated: false,
      gate: (
        <Route element={<EnterpriseRouteGate requiredModule="MANUFACTURING" />}>
          <Route path="/manufacturing/boms" element={<div data-testid="boms">boms</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('login')).toBeInTheDocument();
  });

  it('redirects to /upgrade when the tenant has not purchased the module', () => {
    renderGate({
      modules: ['CORE'],
      gate: (
        <Route element={<EnterpriseRouteGate requiredModule="MANUFACTURING" />}>
          <Route path="/manufacturing/boms" element={<div data-testid="boms">boms</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('upgrade')).toBeInTheDocument();
    expect(screen.queryByTestId('boms')).not.toBeInTheDocument();
  });

  it('redirects to /unauthorized when the user lacks a required permission', () => {
    renderGate({
      roles: ['ADMIN'],
      modules: ['CORE', 'RETAIL_POS'],
      permissions: ['pos.operate'],
      path: '/settings/pos',
      gate: (
        <Route
          element={
            <EnterpriseRouteGate requiredModule="RETAIL_POS" requiredPermission={['pos.supervise']} />
          }
        >
          <Route path="/settings/pos" element={<div data-testid="pos">pos</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('unauthorized')).toBeInTheDocument();
  });

  it('renders the nested route when module and permission both pass', () => {
    renderGate({
      roles: ['ADMIN'],
      modules: ['CORE', 'RETAIL_POS'],
      permissions: ['pos.supervise'],
      path: '/settings/pos',
      gate: (
        <Route
          element={
            <EnterpriseRouteGate requiredModule="RETAIL_POS" requiredPermission={['pos.supervise']} />
          }
        >
          <Route path="/settings/pos" element={<div data-testid="pos">pos</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('pos')).toBeInTheDocument();
  });

  it('lets OWNER through permission checks', () => {
    renderGate({
      roles: ['OWNER'],
      modules: ['CORE', 'RETAIL_POS'],
      permissions: [],
      path: '/settings/pos',
      gate: (
        <Route
          element={
            <EnterpriseRouteGate requiredModule="RETAIL_POS" requiredPermission={['pos.supervise']} />
          }
        >
          <Route path="/settings/pos" element={<div data-testid="pos">pos</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('pos')).toBeInTheDocument();
  });

  it('keeps role walls on /dashboard so office RBAC e2e stays stable', () => {
    renderGate({
      roles: ['VIEWER'],
      modules: ['CORE', 'MANUFACTURING'],
      gate: (
        <Route
          element={
            <EnterpriseRouteGate
              requiredModule="MANUFACTURING"
              roles={['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER']}
            />
          }
        >
          <Route path="/manufacturing/boms" element={<div data-testid="boms">boms</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('dashboard')).toBeInTheDocument();
  });

  it('redirects to /upgrade when none of anyOfModules are entitled', () => {
    renderGate({
      modules: ['CORE'],
      path: '/settings/integrations',
      gate: (
        <Route element={<EnterpriseRouteGate anyOfModules={['SHOPIFY', 'ACCOUNTING']} />}>
          <Route path="/settings/integrations" element={<div data-testid="hub">hub</div>} />
        </Route>
      ),
    });
    expect(screen.getByTestId('upgrade')).toBeInTheDocument();
  });
});
