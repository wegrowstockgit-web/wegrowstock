import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { completeMfaAssertion } from '@/features/settings/networkAccess';
import { DesktopLockOverlay } from './DesktopLockOverlay';

const get = vi.fn();
const post = vi.fn();

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
  },
}));

vi.mock('@/features/settings/networkAccess', () => ({
  completeMfaAssertion: vi.fn(),
}));

describe('DesktopLockOverlay', () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    vi.mocked(completeMfaAssertion).mockReset();
    vi.mocked(completeMfaAssertion).mockResolvedValue({
      mfaCredentialId: 'cred_1',
      mfaChallenge: 'chal',
      mfaSignature: 'sig',
    });
  });

  it('shows password unlock when no passkey is registered', async () => {
    get.mockResolvedValue({ data: { hasPasskey: false, challenge: 'c' } });
    post.mockResolvedValue({ status: 204 });
    const onUnlocked = vi.fn();
    const user = userEvent.setup();
    render(<DesktopLockOverlay open onUnlocked={onUnlocked} />);

    expect(await screen.findByTestId('desktop-unlock-password')).toBeTruthy();
    await user.type(screen.getByTestId('desktop-unlock-password'), 'password123');
    await user.click(screen.getByTestId('desktop-unlock-submit'));
    await waitFor(() => {
      expect(post).toHaveBeenCalledWith('/api/v1/auth/desktop-unlock', { password: 'password123' });
      expect(onUnlocked).toHaveBeenCalled();
    });
  });

  it('shows an error on failed password unlock and can return to passkey', async () => {
    get.mockResolvedValue({
      data: { hasPasskey: true, challenge: 'chal', allowCredentials: [{ id: 'cred_1' }] },
    });
    post.mockRejectedValue(new Error('nope'));
    vi.mocked(completeMfaAssertion).mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    render(<DesktopLockOverlay open onUnlocked={vi.fn()} />);

    expect(await screen.findByTestId('desktop-unlock-use-password')).toBeTruthy();
    await user.click(screen.getByTestId('desktop-unlock-use-password'));
    await user.type(screen.getByTestId('desktop-unlock-password'), 'bad');
    await user.click(screen.getByTestId('desktop-unlock-submit'));
    expect(await screen.findByTestId('desktop-unlock-error')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: /use passkey/i }));
    expect(await screen.findByTestId('desktop-unlock-use-password')).toBeTruthy();
  });

  it('shows use-password fallback while passkey is pending', async () => {
    vi.mocked(completeMfaAssertion).mockImplementation(() => new Promise(() => {}));
    get.mockResolvedValue({
      data: { hasPasskey: true, challenge: 'chal', allowCredentials: [{ id: 'cred_1' }] },
    });
    const onUnlocked = vi.fn();
    const user = userEvent.setup();
    render(<DesktopLockOverlay open onUnlocked={onUnlocked} />);

    expect(await screen.findByTestId('desktop-unlock-use-password')).toBeTruthy();
    await user.click(screen.getByTestId('desktop-unlock-use-password'));
    expect(await screen.findByTestId('desktop-unlock-password')).toBeTruthy();
    expect(onUnlocked).not.toHaveBeenCalled();
  });

  it('unlocks with a successful passkey assertion', async () => {
    get.mockResolvedValue({
      data: { hasPasskey: true, challenge: 'chal', allowCredentials: [{ id: 'cred_1' }] },
    });
    post.mockResolvedValue({ status: 204 });
    const onUnlocked = vi.fn();
    render(<DesktopLockOverlay open onUnlocked={onUnlocked} />);

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith('/api/v1/auth/desktop-unlock', {
        mfaCredentialId: 'cred_1',
        mfaChallenge: 'chal',
        mfaSignature: 'sig',
      });
      expect(onUnlocked).toHaveBeenCalled();
    });
  });
});
