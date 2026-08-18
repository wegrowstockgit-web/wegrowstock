import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useSessionStore } from '@/stores/session';
import { ModuleRouteGate } from './ModuleRouteGate';

function renderGated(modules: string[]) {
  useSessionStore.getState().applyMeProfile({
    userId: 'u1',
    email: 'owner@demo.test',
    displayName: 'Owner',
    roles: ['OWNER'],
    tenantId: 't1',
    enabledModules: modules,
  });
  return render(
    <MemoryRouter initialEntries={['/manufacturing/boms']}>
      <Routes>
        <Route path="/dashboard" element={<div data-testid="dashboard">dashboard</div>} />
        <Route path="/upgrade" element={<div data-testid="upgrade">upgrade</div>} />
        <Route element={<ModuleRouteGate required="MANUFACTURING" />}>
          <Route path="/manufacturing/boms" element={<div data-testid="boms">boms</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('ModuleRouteGate', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: true,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('renders the nested route when entitled', () => {
    renderGated(['CORE', 'MANUFACTURING']);
    expect(screen.getByTestId('boms')).toBeInTheDocument();
  });

  it('redirects to /upgrade when the module is not purchased', () => {
    renderGated(['CORE']);
    expect(screen.getByTestId('upgrade')).toBeInTheDocument();
    expect(screen.queryByTestId('boms')).not.toBeInTheDocument();
  });
});
