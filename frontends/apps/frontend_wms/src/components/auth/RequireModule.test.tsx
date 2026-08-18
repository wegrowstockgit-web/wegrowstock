import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useSessionStore } from '@/stores/session';
import { RequireModule } from './RequireModule';

describe('RequireModule', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: true,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('renders children when the tenant owns the module', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't1',
      enabledModules: ['CORE', 'MANUFACTURING'],
    });
    render(
      <RequireModule required="MANUFACTURING">
        <div data-testid="gated">visible</div>
      </RequireModule>,
    );
    expect(screen.getByTestId('gated')).toBeInTheDocument();
  });

  it('returns null when the tenant has not purchased the module', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@acme.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't2',
      enabledModules: ['CORE'],
    });
    render(
      <RequireModule required="MANUFACTURING">
        <div data-testid="gated">visible</div>
      </RequireModule>,
    );
    expect(screen.queryByTestId('gated')).not.toBeInTheDocument();
  });
});
