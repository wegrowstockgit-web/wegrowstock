import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { PageHelpOverlay } from './PageHelpOverlay';

function mockMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

function NavigateButton({ to }: { to: string }) {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => navigate(to)}>
      Go {to}
    </button>
  );
}

describe('PageHelpOverlay', () => {
  beforeEach(() => {
    mockMatchMedia(false);
  });

  it('opens portaled panel with sales-order playbook', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-panel')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-panel').parentElement?.parentElement).toBe(document.body);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Confirm customer demand/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Un-allocate/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Who else this affects/i);
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Key elements/i);
  });

  it('opens inbound receive playbook', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/inbound/receive']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-drawer')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-body')).toHaveTextContent(/Scan freight into inventory/i);
  });

  it('shows fallback playbook for unregistered routes', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/totally-unknown-route']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-fallback')).toBeInTheDocument());
    expect(screen.getByTestId('page-help-fallback')).toHaveTextContent(/totally-unknown-route/);
    expect(screen.getByTestId('page-help-fallback')).toHaveTextContent(/ERROR_CORRECTION/i);
  });

  it('closes via Escape and backdrop', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <PageHelpOverlay />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-body')).toBeVisible());

    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() =>
      expect(screen.queryByTestId('page-help-body')).not.toBeInTheDocument(),
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-body')).toBeVisible());
    await user.click(screen.getByLabelText('Close page help'));
    await waitFor(() =>
      expect(screen.queryByTestId('page-help-body')).not.toBeInTheDocument(),
    );
  });

  it('closes when the route changes', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/sales-orders']}>
        <Routes>
          <Route
            path="*"
            element={
              <>
                <PageHelpOverlay />
                <NavigateButton to="/products" />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('page-help-trigger'));
    await waitFor(() => expect(screen.getByTestId('page-help-panel')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Go /products' }));
    await waitFor(() =>
      expect(screen.queryByTestId('page-help-panel')).not.toBeInTheDocument(),
    );
  });
});
