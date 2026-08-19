import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ActivityTimeline } from './ActivityTimeline';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

function renderTimeline() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ActivityTimeline entityType="USER" entityId="user-1" />
    </QueryClientProvider>,
  );
}

describe('ActivityTimeline', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('shows a plain-language change instead of raw JSON', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [
        {
          id: 'evt-1',
          actorDisplayName: null,
          actorEmail: null,
          action: 'TG_UPDATE',
          entityType: 'USER',
          entityId: 'user-1',
          createdAt: '2026-08-18T20:42:36.000Z',
          diff: {
            op: 'UPDATE',
            old: {
              id: 'user-1',
              tenant_id: 'tenant-1',
              email: 'client.b@demo.test',
              display_name: 'Retail B',
              status: 'ACTIVE',
              mfa_enabled: false,
              updated_at: '2026-08-18T19:00:00.000Z',
            },
            new: {
              id: 'user-1',
              tenant_id: 'tenant-1',
              email: 'client.b@demo.test',
              display_name: 'Retail B',
              status: 'INACTIVE',
              mfa_enabled: false,
              updated_at: '2026-08-18T20:42:36.000Z',
            },
          },
        },
      ],
    });

    renderTimeline();

    expect(
      await screen.findByText('Changed status from Active to Inactive'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('timeline-event-evt-1')).toHaveTextContent('System');
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    expect(screen.queryByText(/"tenant_id"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Hide changes|Show changes/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/BEFORE/i)).not.toBeInTheDocument();
  });

  it('shows a shield and IP line for login events', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [
        {
          id: 'login-1',
          actorDisplayName: 'Demo Owner',
          actorEmail: 'owner@demo.test',
          action: 'LOGIN_SUCCESS',
          entityType: 'USER',
          entityId: 'user-1',
          createdAt: '2026-08-19T12:00:00.000Z',
          diff: { ip: '198.51.100.45', location: 'Dallas, TX, US' },
        },
        {
          id: 'block-1',
          actorDisplayName: 'Demo Owner',
          actorEmail: 'owner@demo.test',
          action: 'LOGIN_BLOCKED_CIDR',
          entityType: 'USER',
          entityId: 'user-1',
          createdAt: '2026-08-19T11:00:00.000Z',
          diff: { ip: '203.0.113.40', location: 'Dallas, TX, US' },
        },
      ],
    });

    renderTimeline();

    expect(await screen.findByText('Signed in')).toBeInTheDocument();
    expect(screen.getByTestId('timeline-login-success-icon')).toBeInTheDocument();
    expect(screen.getByTestId('timeline-login-blocked-icon')).toBeInTheDocument();
    expect(screen.getByText('198.51.100.45 • Dallas, TX, US')).toBeInTheDocument();
    expect(screen.getByText('Blocked sign-in (off-network)')).toBeInTheDocument();
    expect(screen.queryByText(/"ip":/)).not.toBeInTheDocument();
  });
});
