import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NotFoundPage } from './NotFoundPage';

vi.mock('@/stores/session', () => ({
  useIsAuthenticated: () => true,
  useSessionRoles: () => ['OWNER'],
  isExclusiveRole: () => false,
}));

vi.mock('@/stores/activeWarehouse', () => ({
  useActiveWarehouseStore: (sel: (s: { warehouse: null }) => unknown) =>
    sel({ warehouse: null }),
}));

describe('NotFoundPage', () => {
  it('renders 404 messaging and home CTA', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('not-found-page')).toBeInTheDocument();
    expect(screen.getByText('Page not found')).toBeInTheDocument();
    expect(screen.getByTestId('not-found-home')).toHaveTextContent('Return to Dashboard');
  });
});
