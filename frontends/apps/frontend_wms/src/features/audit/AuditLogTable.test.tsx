import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuditLogTable } from './AuditLogTable';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn() },
}));

function renderTable() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AuditLogTable />
    </QueryClientProvider>,
  );
}

describe('AuditLogTable', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
  });

  it('shows a plain-language change instead of raw JSON', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        items: [
          {
            id: 'evt-1',
            actorDisplayName: 'Demo Owner',
            action: 'TG_INSERT',
            entityType: 'USERS',
            entityId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            createdAt: '2026-08-17T14:17:54.000Z',
            diff: {
              op: 'INSERT',
              table: 'users',
              new: { email: 'ing.arturo@demo.test', display_name: 'Arturo' },
            },
          },
        ],
      },
    });

    renderTable();

    expect(await screen.findByText('Added ing.arturo@demo.test')).toBeInTheDocument();
    expect(screen.getByTestId('audit-row-evt-1')).toHaveTextContent('Created');
    expect(screen.getByTestId('audit-row-evt-1')).toHaveTextContent('User');
    expect(screen.getByText('Demo Owner')).toBeInTheDocument();
    expect(screen.queryByText(/"op":"INSERT"/)).not.toBeInTheDocument();
  });

  it('opens readable field details from a row', async () => {
    const user = userEvent.setup();
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        items: [
          {
            id: 'evt-2',
            actorDisplayName: 'Demo Owner',
            action: 'TG_UPDATE',
            entityType: 'USERS',
            entityId: 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff',
            createdAt: '2026-08-17T14:18:00.000Z',
            diff: {
              op: 'UPDATE',
              old: { status: 'ACTIVE', email: 'a@demo.test' },
              new: { status: 'INACTIVE', email: 'a@demo.test' },
            },
          },
        ],
      },
    });

    renderTable();
    expect(await screen.findByText('Changed status')).toBeInTheDocument();
    await user.click(screen.getByText('Changed status'));
    expect(await screen.findByTestId('audit-diff-detail')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('INACTIVE')).toBeInTheDocument();
  });
});
