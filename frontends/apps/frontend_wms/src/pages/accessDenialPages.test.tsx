import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { UpgradePage } from './UpgradePage';
import { UnauthorizedPage } from './UnauthorizedPage';

describe('access denial pages', () => {
  it('renders the upgrade page', () => {
    render(
      <MemoryRouter>
        <UpgradePage />
      </MemoryRouter>,
    );
    expect(screen.getByTestId('upgrade-page')).toBeInTheDocument();
    expect(screen.getByTestId('upgrade-home')).toBeInTheDocument();
  });

  it('renders the unauthorized page', () => {
    render(
      <MemoryRouter>
        <UnauthorizedPage />
      </MemoryRouter>,
    );
    expect(screen.getByTestId('unauthorized-page')).toBeInTheDocument();
    expect(screen.getByTestId('unauthorized-home')).toBeInTheDocument();
  });
});
