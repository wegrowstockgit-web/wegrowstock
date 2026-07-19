import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { NetworkStatusBadge } from './NetworkStatusBadge';
import { useNetworkSyncStore } from '@/stores/networkSyncStore';

vi.mock('@/offline/mutationQueue', () => ({
  getMutationQueue: vi.fn(async () => []),
}));

describe('NetworkStatusBadge', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
    useNetworkSyncStore.setState({ online: true, syncing: false, pendingCount: 0 });
  });

  afterEach(() => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
    vi.restoreAllMocks();
  });

  it('shows Connected when online and queue empty', async () => {
    await act(async () => {
      render(<NetworkStatusBadge />);
    });
    expect(screen.getByTestId('network-status-badge')).toHaveAttribute('data-phase', 'online');
    expect(screen.getByText('Connected')).toBeInTheDocument();
  });

  it('shows Offline caching badge when offline', async () => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false });
    useNetworkSyncStore.setState({ online: false, syncing: false, pendingCount: 2 });
    await act(async () => {
      render(<NetworkStatusBadge />);
    });
    expect(screen.getByTestId('network-status-badge')).toHaveAttribute('data-phase', 'offline');
    expect(screen.getByText(/Offline - Caching Scans/i)).toBeInTheDocument();
  });

  it('shows Syncing when online with pending queue', async () => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
    useNetworkSyncStore.setState({ online: true, syncing: true, pendingCount: 1 });
    await act(async () => {
      render(<NetworkStatusBadge />);
    });
    expect(screen.getByTestId('network-status-badge')).toHaveAttribute('data-phase', 'syncing');
    expect(screen.getByText(/Syncing/i)).toBeInTheDocument();
  });
});
