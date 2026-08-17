import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App, AppRoutes } from './App';
import { demoSession } from '@/lib/posSession';
import { PosSessionProvider } from '@/lib/PosSessionContext';
import { lockShift, unlockShift } from '@/components/ScannerSecurityGate';
import { seedDemoManagerPinsIfEmpty } from '@/offline/pinVault';

vi.mock('@/lib/syncWorker', () => ({
  startOutboxPolling: () => () => undefined,
  downloadCatalog: async () => 0,
}));

describe('App', () => {
  afterEach(() => {
    lockShift();
  });

  it('sends unauthenticated visitors to full-screen login', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('pos-login')).toBeTruthy();
    expect(screen.queryByTestId('register-page')).toBeNull();
    expect(screen.queryByTestId('pos-signin')).toBeNull();
  });

  it('redirects /register to login when there is no session', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('pos-login')).toBeTruthy();
  });

  it('shows a fallback for unknown routes', () => {
    render(
      <MemoryRouter initialEntries={['/missing']}>
        <App />
      </MemoryRouter>,
    );
    expect(screen.getByText('Back to register')).toBeTruthy();
  });

  it('renders login', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('pos-login')).toBeTruthy();
  });

  it('requires a shift PIN before the register renders', async () => {
    lockShift();
    seedDemoManagerPinsIfEmpty();
    render(
      <MemoryRouter initialEntries={['/']}>
        <PosSessionProvider initial={demoSession()} disableFetch>
          <AppRoutes />
        </PosSessionProvider>
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('pos-pin-gate')).toBeTruthy();
    expect(screen.queryByTestId('register-page')).toBeNull();
  });

  it('renders the register after the shift is unlocked', async () => {
    seedDemoManagerPinsIfEmpty();
    unlockShift();
    render(
      <MemoryRouter initialEntries={['/']}>
        <PosSessionProvider initial={demoSession()} disableFetch>
          <AppRoutes />
        </PosSessionProvider>
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('register-page')).toBeTruthy();
  });
});
