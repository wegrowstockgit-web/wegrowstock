import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SettingsPage } from './SettingsPage';
import { useSessionStore } from '@/stores/session';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    patch: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn().mockResolvedValue({ data: {} }),
    put: vi.fn().mockResolvedValue({ data: {} }),
    delete: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

vi.mock('@/api/users', () => ({
  userApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn() },
}));

vi.mock('@/components/ui/Toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

function renderSettings(path = '/settings?tab=profile') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <SettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SettingsPage Retail POS tab', () => {
  beforeEach(() => {
    useSessionStore.setState({
      authenticated: true,
      mfaVerified: false,
      user: null,
      primarySession: null,
      lastRequestId: null,
    });
  });

  it('shows Retail POS for an entitled owner', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u1',
      email: 'owner@demo.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't1',
      enabledModules: ['CORE', 'RETAIL_POS'],
    });
    renderSettings();
    expect(screen.getByTestId('settings-tab-retailPos')).toBeInTheDocument();
  });

  it('hides Retail POS when the module is not entitled', () => {
    useSessionStore.getState().applyMeProfile({
      userId: 'u2',
      email: 'owner@acme.test',
      displayName: 'Owner',
      roles: ['OWNER'],
      tenantId: 't2',
      enabledModules: ['CORE'],
    });
    renderSettings('/settings?tab=retailPos');
    expect(screen.queryByTestId('settings-tab-retailPos')).not.toBeInTheDocument();
    expect(screen.queryByTestId('pos-settings-panel')).not.toBeInTheDocument();
  });
});
