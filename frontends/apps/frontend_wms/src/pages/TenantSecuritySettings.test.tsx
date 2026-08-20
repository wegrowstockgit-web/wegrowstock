import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantSecuritySettings } from './TenantSecuritySettings';

const get = vi.fn();
const put = vi.fn();
const patch = vi.fn();
const post = vi.fn();

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => get(...args),
    put: (...args: unknown[]) => put(...args),
    patch: (...args: unknown[]) => patch(...args),
    post: (...args: unknown[]) => post(...args),
  },
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <TenantSecuritySettings />
    </QueryClientProvider>,
  );
}

describe('TenantSecuritySettings desktop idle timeout', () => {
  beforeEach(() => {
    get.mockReset();
    put.mockReset();
    patch.mockReset();
    post.mockReset();
    get.mockImplementation(async (url: string) => {
      if (url === '/api/v1/settings') {
        return { data: { desktop_idle_timeout_minutes: 30 } };
      }
      if (url === '/api/v1/settings/sso') {
        return {
          data: {
            issuerUrl: '',
            clientId: '',
            enabled: false,
            forceSso: false,
            protocol: 'OIDC',
            ssoProvider: 'CUSTOM',
            configured: false,
            corporateCidrIps: [],
          },
        };
      }
      if (url === '/api/v1/settings/sso/connection-states') {
        return { data: { providers: [] } };
      }
      if (url === '/api/v1/settings/email-domains') {
        return { data: [] };
      }
      return { data: {} };
    });
    patch.mockResolvedValue({ data: { desktop_idle_timeout_minutes: 15 } });
  });

  it('saves the desktop idle timeout through the settings mutation', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByTestId('desktop-idle-timeout')).toBeTruthy();
    await user.selectOptions(screen.getByTestId('desktop-idle-timeout'), '15');
    await user.click(screen.getByTestId('desktop-idle-timeout-save'));
    await waitFor(() => {
      expect(patch).toHaveBeenCalledWith('/api/v1/settings', {
        desktop_idle_timeout_minutes: 15,
      });
    });
  });
});
