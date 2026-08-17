import { beforeEach, describe, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { LoginPage } from './LoginPage';
import { apiClient } from '@/api/client';
import { resetMagicLinkClaimsForTests } from '@/lib/magicLinkConsume';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

vi.mock('@/lib/terminalPasskey', () => ({
  readTerminalPasskey: () => ({
    credentialId: 'cred_1',
    secret: 'secret',
    userId: 'u1',
    tenantId: 't1',
  }),
}));

function wrap(ui: ReactNode, initialEntry = '/login') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialEntry]}>
        {ui}
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage MFA intercept', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    resetMagicLinkClaimsForTests();
  });

  it('opens passkey challenge on MFA_REQUIRED_FOR_EXTERNAL_ACCESS then reissues login', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (url: string) => {
      if (String(url).includes('/auth/discovery')) {
        return { data: { isPasswordAllowed: true } };
      }
      if (String(url).includes('/auth/me')) {
        return {
          data: {
            userId: 'u1',
            tenantId: 't1',
            email: 'owner@demo.test',
            displayName: 'Owner',
            roles: ['OWNER'],
          },
        };
      }
      return { data: {} };
    });
    vi.mocked(apiClient.post)
      .mockRejectedValueOnce({
        response: {
          data: {
            title: 'MFA_REQUIRED_FOR_EXTERNAL_ACCESS',
            challenge: 'chal',
            allowCredentials: [{ id: 'cred_1' }],
          },
        },
      })
      .mockResolvedValueOnce({
        data: { tenantId: 't1', userId: 'u1', roles: ['OWNER'], warehouseIds: [], grantedPermissions: [] },
      });

    wrap(<LoginPage />);

    fireEvent.click(screen.getByTestId('login-continue'));
    expect(await screen.findByTestId('login-submit')).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('login-password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('login-mfa-challenge')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('login-mfa-submit'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/v1/auth/login',
        expect.objectContaining({
          email: 'owner@demo.test',
          mfaCredentialId: 'cred_1',
          mfaChallenge: 'chal',
          mfaSignature: expect.any(String),
        }),
      );
    });
  });
});

describe('LoginPage magic link', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
    resetMagicLinkClaimsForTests();
  });

  it('does not consume the token when requesting an email link', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { isPasswordAllowed: true } });
    vi.mocked(apiClient.post).mockResolvedValue({ data: { status: 'accepted', magicToken: 'should-not-consume' } });

    wrap(<LoginPage />);
    fireEvent.click(screen.getByTestId('login-continue'));
    expect(await screen.findByTestId('login-magic-link')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('login-magic-link'));

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/auth/magic-login', {
        email: 'owner@demo.test',
      });
    });
    expect(apiClient.post).not.toHaveBeenCalledWith(
      '/api/v1/auth/magic-login/consume',
      expect.anything(),
    );
    expect(await screen.findByText(/open Mailpit/i)).toBeInTheDocument();
  });

  it('consumes a URL magic token only once under Strict Mode', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { tenantId: 't1', userId: 'u1', roles: ['OWNER'], warehouseIds: [], grantedPermissions: [] },
    });
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        userId: 'u1',
        tenantId: 't1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
      },
    });

    wrap(
      <StrictMode>
        <LoginPage />
      </StrictMode>,
      '/login?magic=tok-once',
    );

    await waitFor(() => {
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/auth/magic-login/consume', { token: 'tok-once' });
    });
    expect(
      vi.mocked(apiClient.post).mock.calls.filter((call) => String(call[0]).includes('magic-login/consume')),
    ).toHaveLength(1);
  });
});
