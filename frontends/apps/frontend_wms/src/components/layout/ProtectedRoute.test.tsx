import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { useSessionStore } from '@/stores/session';

describe('ProtectedRoute', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: false,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('renders children for an authenticated office user', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't1',
      enabledModules: ['CORE'],
    });
    render(
      <MemoryRouter>
        <ProtectedRoute officeOnly>
          <div data-testid="shell">shell</div>
        </ProtectedRoute>
      </MemoryRouter>,
    );
    expect(screen.getByTestId('shell')).toBeInTheDocument();
  });

  it('sends anonymous users to login', () => {
    render(
      <MemoryRouter initialEntries={['/reports']}>
        <Routes>
          <Route path="/login" element={<div data-testid="login">login</div>} />
          <Route
            path="/reports"
            element={
              <ProtectedRoute roles={['OWNER', 'ADMIN']}>
                <div data-testid="reports">reports</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByTestId('login')).toBeInTheDocument();
  });
});
