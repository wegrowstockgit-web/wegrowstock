import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Header } from './Header';

vi.mock('@/stores/session', () => ({
  useSessionStore: (sel: (s: { user: { displayName: string; email: string } }) => unknown) =>
    sel({ user: { displayName: 'Picker', email: 'picker@demo.test' } }),
}));

vi.mock('@/stores/offlineStore', () => ({
  useOfflineStore: (sel: (s: { quarantinedMutations: unknown[] }) => unknown) =>
    sel({ quarantinedMutations: [] }),
}));

vi.mock('@/stores/rail', () => ({
  useRailStore: (sel: (s: { toggleMobileOpen: () => void }) => unknown) =>
    sel({ toggleMobileOpen: () => undefined }),
}));

vi.mock('@/components/layout/NetworkStatusBadge', () => ({
  NetworkStatusBadge: () => <span data-testid="network-status-badge">Connected</span>,
}));

vi.mock('@/components/layout/ProfileSettingsDialog', () => ({
  ProfileSettingsDialog: () => null,
}));

vi.mock('@/components/layout/TerminalPinPad', () => ({
  TerminalPinPad: () => null,
}));

vi.mock('@/components/layout/FloorPunchClock', () => ({
  FloorPunchClock: () => null,
}));

const baseProps = {
  title: 'Floor ops',
  warehouses: [] as never[],
  warehouse: null,
  hideSwitcher: true,
  lockTitle: '',
  lockReason: null,
  onToggleCommandPalette: () => undefined,
  onWarehouseChange: () => undefined,
  onSignOut: () => undefined,
};

describe('Header network badge gating', () => {
  it('shows Connected badge on warehouse / device header', () => {
    render(
      <MemoryRouter>
        <Header {...baseProps} isWarehouseView />
      </MemoryRouter>,
    );
    expect(screen.getByTestId('network-status-badge')).toBeInTheDocument();
  });

  it('hides Connected badge on office header', () => {
    render(
      <MemoryRouter>
        <Header {...baseProps} title="" isWarehouseView={false} />
      </MemoryRouter>,
    );
    expect(screen.queryByTestId('network-status-badge')).not.toBeInTheDocument();
    expect(screen.queryByTestId('brand-logo')).not.toBeInTheDocument();
  });
});
