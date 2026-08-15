import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { SyncConflictAlertBanner } from './SyncConflictAlertBanner';

const getMock = vi.fn();

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
  },
}));

function renderBanner() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SyncConflictAlertBanner />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SyncConflictAlertBanner', () => {
  it('renders high-visibility warning when pending conflicts exist', async () => {
    getMock.mockResolvedValue({
      data: [
        {
          id: 'c1',
          errorMessage: 'BIN_FULL: allocated bin location is full',
          status: 'PENDING',
          payload: {},
          createdAt: new Date().toISOString(),
        },
      ],
    });
    renderBanner();
    await waitFor(() => expect(screen.getByTestId('sync-conflict-alert-banner')).toBeInTheDocument());
    expect(screen.getByTestId('sync-conflict-resolve-now')).toBeInTheDocument();
    expect(screen.getByText(/offline scan failed to sync/i)).toBeInTheDocument();
  });

  it('renders nothing when the queue is empty', async () => {
    getMock.mockResolvedValue({ data: [] });
    const { container } = renderBanner();
    await waitFor(() => expect(getMock).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('pluralizes the banner copy for multiple parked scans', async () => {
    getMock.mockResolvedValue({
      data: [
        { id: 'c1', errorMessage: 'a', status: 'PENDING', payload: {}, createdAt: new Date().toISOString() },
        { id: 'c2', errorMessage: 'b', status: 'PENDING', payload: {}, createdAt: new Date().toISOString() },
      ],
    });
    renderBanner();
    await waitFor(() => expect(screen.getByText(/2 offline scans failed to sync/i)).toBeInTheDocument());
  });
});
