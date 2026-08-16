import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from './App';

vi.mock('@/lib/syncWorker', () => ({
  startOutboxPolling: () => () => undefined,
}));

describe('App', () => {
  it('renders the register at /', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('register-page')).toBeTruthy();
  });

  it('redirects /register to the register page', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <App />
      </MemoryRouter>,
    );
    expect(await screen.findByTestId('register-page')).toBeTruthy();
  });

  it('shows a fallback for unknown routes', () => {
    render(
      <MemoryRouter initialEntries={['/missing']}>
        <App />
      </MemoryRouter>,
    );
    expect(screen.getByText('Back to register')).toBeTruthy();
  });

  it('renders login', () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>,
    );
    expect(screen.getByTestId('pos-login')).toBeTruthy();
  });
});
