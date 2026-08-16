import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PosSessionProvider, usePosSession } from './PosSessionContext';
import { demoSession } from './posSession';

function Probe() {
  const { session, t } = usePosSession();
  return (
    <div>
      <span data-testid="probe-lang">{session.language}</span>
      <span data-testid="probe-copy">{t('register.add')}</span>
    </div>
  );
}

describe('PosSessionProvider', () => {
  it('uses an injected session without fetching', () => {
    render(
      <PosSessionProvider initial={demoSession({ language: 'fr' })} disableFetch>
        <Probe />
      </PosSessionProvider>,
    );
    expect(screen.getByTestId('probe-lang')).toHaveTextContent('fr');
    expect(screen.getByTestId('probe-copy')).toHaveTextContent('Ajouter');
  });

  it('loads a live session when the API is entitled', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({
          posEnabled: true,
          language: 'es',
          languageSource: 'ORGANIZATION',
          currency: 'MXN',
          currencySource: 'WMS',
          companyName: 'Tienda',
        }),
      }),
    );
    render(
      <PosSessionProvider>
        <Probe />
      </PosSessionProvider>,
    );
    await waitFor(() => {
      expect(screen.getByTestId('probe-lang')).toHaveTextContent('es');
      expect(screen.getByTestId('probe-copy')).toHaveTextContent('Añadir');
    });
  });
});
