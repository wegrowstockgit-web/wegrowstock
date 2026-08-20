import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DesktopIdleGate } from './DesktopIdleGate';

vi.mock('@/hooks/useDesktopIdle', () => ({
  useDesktopIdle: () => ({
    isWarningPhase: true,
    isLocked: false,
    staySignedIn: vi.fn(),
    unlock: vi.fn(),
  }),
}));

describe('DesktopIdleGate', () => {
  it('mounts the warning modal while unlocked', () => {
    render(<DesktopIdleGate />);
    expect(screen.getByTestId('idle-warning-modal')).toBeTruthy();
    expect(screen.queryByTestId('desktop-lock-overlay')).toBeNull();
  });
});
