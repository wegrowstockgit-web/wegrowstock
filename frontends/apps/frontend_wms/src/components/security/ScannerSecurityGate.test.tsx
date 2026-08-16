import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ScannerSecurityGate } from './ScannerSecurityGate';

vi.mock('@/hooks/useScannerIdle', () => ({
  useScannerIdle: () => undefined,
}));

vi.mock('@/offline/mutationQueue', () => ({
  installMutationQueueTestHook: () => undefined,
}));

vi.mock('@/stores/scannerLockStore', () => ({
  installScannerLockTestHook: () => undefined,
  useScannerLockStore: (sel: (s: {
    hydrate: () => Promise<void>;
    hydrated: boolean;
    resetLockState: () => void;
  }) => unknown) =>
    sel({
      hydrate: async () => undefined,
      hydrated: true,
      resetLockState: () => undefined,
    }),
}));

vi.mock('@/stores/session', () => ({
  useIsAuthenticated: () => true,
}));

vi.mock('@/components/security/ScannerLockOverlay', () => ({
  ScannerLockOverlay: () => null,
}));

vi.mock('@/components/security/ScannerPinSetupOverlay', () => ({
  ScannerPinSetupOverlay: () => null,
}));

describe('ScannerSecurityGate', () => {
  it('renders a typed scan fallback on floor routes when hardware APIs are missing', () => {
    render(
      <MemoryRouter initialEntries={['/fulfillment']}>
        <ScannerSecurityGate>
          <div>floor</div>
        </ScannerSecurityGate>
      </MemoryRouter>,
    );

    expect(screen.queryByRole('button', { name: /connect bluetooth scale/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /connect usb scanner/i })).toBeNull();
    expect(screen.getByTestId('hardware-manual-fallback')).toBeTruthy();
    expect(screen.getByLabelText('Manual scan')).toBeTruthy();
  });

  it('does not render the hardware fallback on office routes', () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <ScannerSecurityGate>
          <div>office</div>
        </ScannerSecurityGate>
      </MemoryRouter>,
    );

    expect(screen.queryByTestId('hardware-manual-fallback')).toBeNull();
  });
});
