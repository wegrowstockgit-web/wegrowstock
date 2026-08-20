import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { PlatformAuditTrail } from './PlatformAuditTrail';
import { fetchAuditLogs } from './api';

vi.mock('./api', async () => {
  const actual = await vi.importActual<typeof import('./api')>('./api');
  return {
    ...actual,
    fetchAuditLogs: vi.fn(),
  };
});

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('PlatformAuditTrail impersonation filter', () => {
  beforeEach(() => {
    vi.mocked(fetchAuditLogs).mockReset();
    vi.mocked(fetchAuditLogs).mockImplementation(async (_limit, impersonationOnly) => {
      if (impersonationOnly) {
        return [
          {
            id: '2',
            adminId: 'a1',
            adminEmail: 'owner@demo.test',
            action: 'TENANT_IMPERSONATE',
            targetTenantId: 't-1',
            diffJson: '{}',
            ipAddress: '10.0.0.1',
            createdAt: '2026-08-19T12:00:00Z',
            actorType: 'PLATFORM_ADMIN_IMPERSONATION',
          },
        ];
      }
      return [
        {
          id: '1',
          adminId: 'a1',
          adminEmail: 'owner@demo.test',
          action: 'TENANT_STATUS_UPDATE',
          targetTenantId: 't-1',
          diffJson: '{}',
          ipAddress: '10.0.0.1',
          createdAt: '2026-08-19T12:00:00Z',
          actorType: 'PLATFORM_ADMIN',
        },
        {
          id: '2',
          adminId: 'a1',
          adminEmail: 'owner@demo.test',
          action: 'TENANT_IMPERSONATE',
          targetTenantId: 't-1',
          diffJson: '{}',
          ipAddress: '10.0.0.1',
          createdAt: '2026-08-19T12:00:00Z',
          actorType: 'PLATFORM_ADMIN_IMPERSONATION',
        },
      ];
    });
  });

  it('highlights impersonation rows and filters to impersonation-only', async () => {
    wrap(<PlatformAuditTrail />);
    expect(await screen.findByTestId('platform-audit')).toBeTruthy();
    expect(screen.getByTestId('impersonation-badge')).toBeTruthy();
    expect(screen.getByText('TENANT_STATUS_UPDATE')).toBeTruthy();

    fireEvent.click(screen.getByTestId('audit-impersonation-filter'));
    await waitFor(() => {
      expect(fetchAuditLogs).toHaveBeenCalledWith(100, true);
    });
    expect(await screen.findByTestId('audit-impersonation-row')).toBeTruthy();
    expect(screen.queryByText('TENANT_STATUS_UPDATE')).toBeNull();
  });
});
