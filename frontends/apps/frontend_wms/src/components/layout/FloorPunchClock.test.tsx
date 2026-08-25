import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { FloorPunchClock } from './FloorPunchClock';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({
      data: { shiftId: null, warehouseId: null, clockIn: null, clockOut: null, currentActivity: null, active: false },
    }),
    post: vi.fn(),
  },
}));

vi.mock('@/stores/activeWarehouse', () => ({
  useActiveWarehouseStore: (sel: (s: { warehouseId: string | null }) => unknown) =>
    sel({ warehouseId: 'wh-1' }),
}));

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('FloorPunchClock', () => {
  it('hides hardware connect buttons and shows a manual fallback when APIs are missing', () => {
    wrap(<FloorPunchClock />);

    expect(screen.getByTestId('floor-punch-clock')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /connect bluetooth scale/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /connect usb scanner/i })).toBeNull();
    expect(screen.getByTestId('hardware-manual-fallback')).toBeTruthy();
    expect(screen.getByLabelText('Manual weight')).toBeTruthy();
  });

  it('collapses to a clock icon menu on scanner-width screens', () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query.includes('max-width: 767px'),
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }));
    wrap(<FloorPunchClock warehouseSized />);
    expect(screen.getByTestId('labor-clock-menu-toggle')).toBeTruthy();
    expect(screen.queryByTestId('labor-clock-in')).toBeNull();
    expect(screen.queryByTestId('hardware-manual-fallback')).toBeNull();
  });
});
