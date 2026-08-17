import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PosSettingsPanel } from './PosSettingsPanel';

const patch = vi.fn();
const get = vi.fn();

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => get(...args),
    patch: (...args: unknown[]) => patch(...args),
  },
}));

vi.mock('@/components/ui/Toast', () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

function renderPanel() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <PosSettingsPanel />
    </QueryClientProvider>,
  );
}

describe('PosSettingsPanel', () => {
  beforeEach(() => {
    get.mockReset();
    patch.mockReset();
    get.mockResolvedValue({
      data: {
        pos_default_currency: 'USD',
        pos_enable_cfdi_invoicing: false,
        pos_receipt_header: 'Demo Corp',
        pos_receipt_footer: 'Thanks',
        pos_require_blind_closeout: false,
      },
    });
    patch.mockResolvedValue({ data: {} });
  });

  it('loads three cards and saves POS settings via PATCH', async () => {
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => expect(screen.getByTestId('pos-receipt-header')).toHaveValue('Demo Corp'));
    expect(screen.getByTestId('pos-settings-localization')).toBeInTheDocument();
    expect(screen.getByTestId('pos-settings-receipt')).toBeInTheDocument();
    expect(screen.getByTestId('pos-settings-security')).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId('pos-default-currency'), 'MXN');
    await user.click(screen.getByTestId('pos-enable-cfdi'));
    await user.clear(screen.getByTestId('pos-receipt-footer'));
    await user.type(screen.getByTestId('pos-receipt-footer'), 'Cambios en 14 dias');
    await user.click(screen.getByTestId('pos-require-blind-closeout'));
    await user.click(screen.getByTestId('pos-settings-save'));

    await waitFor(() => expect(patch).toHaveBeenCalled());
    expect(patch.mock.calls[0][0]).toBe('/api/v1/settings');
    expect(patch.mock.calls[0][1]).toMatchObject({
      pos_default_currency: 'MXN',
      pos_enable_cfdi_invoicing: true,
      pos_receipt_header: 'Demo Corp',
      pos_receipt_footer: 'Cambios en 14 dias',
      pos_require_blind_closeout: true,
    });
  });
});
