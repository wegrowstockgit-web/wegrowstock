import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useSessionStore } from '@/stores/session';
import { RequireRole } from './RequireRole';

describe('RequireRole', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: true,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('shows children for warehouse managers', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'manager@demo.test',
      displayName: 'Manager',
      roles: ['WAREHOUSE_MANAGER'],
      tenantId: 't1',
    });
    render(
      <RequireRole roles={['WAREHOUSE_MANAGER', 'ADMIN']}>
        <div data-testid="gated">visible</div>
      </RequireRole>,
    );
    expect(screen.getByTestId('gated')).toBeInTheDocument();
  });

  it('hides children for pickers', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u2',
      email: 'picker@demo.test',
      displayName: 'Picker',
      roles: ['PICKER'],
      tenantId: 't1',
    });
    render(
      <RequireRole roles={['WAREHOUSE_MANAGER', 'ADMIN']}>
        <div data-testid="gated">visible</div>
      </RequireRole>,
    );
    expect(screen.queryByTestId('gated')).not.toBeInTheDocument();
  });
});
