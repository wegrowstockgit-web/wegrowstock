import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  POS_SESSION_CACHE_KEY,
  applySessionDto,
  defaultSessionState,
  demoSession,
  fetchPosSession,
  languageForUi,
  readCachedSession,
  writeCachedSession,
} from './posSession';

const place = {
  language: 'en' as const,
  currency: 'USD',
  timezone: 'America/New_York',
  localeTag: 'en-US',
  taxRegion: 'US' as const,
};

describe('pos session', () => {
  afterEach(() => {
    localStorage.removeItem(POS_SESSION_CACHE_KEY);
  });

  it('applies WMS language and currency only when POS is entitled', () => {
    const enabled = applySessionDto(
      {
        posEnabled: true,
        language: 'es',
        languageSource: 'ORGANIZATION',
        currency: 'EUR',
        currencySource: 'WMS',
        placeCurrency: 'MXN',
        taxRegionHint: 'MX',
        tenantBaseCurrency: 'EUR',
        liveExchangeRate: 18.5,
        companyName: 'Demo',
      },
      place,
    );
    expect(enabled.language).toBe('es');
    expect(enabled.currency).toBe('EUR');
    expect(enabled.placeCurrency).toBe('MXN');
    expect(enabled.taxRegion).toBe('MX');
    expect(enabled.tenantBaseCurrency).toBe('EUR');
    expect(enabled.liveExchangeRate).toBe(18.5);

    const locked = applySessionDto(
      {
        posEnabled: false,
        language: 'es',
        currency: 'EUR',
        placeLanguage: 'fr',
        placeCurrency: 'GBP',
      },
      { ...place, language: 'fr', currency: 'GBP', localeTag: 'fr-FR' },
    );
    expect(locked.posEnabled).toBe(false);
    expect(locked.language).toBe('fr');
    expect(locked.currency).toBe('GBP');
    expect(languageForUi(locked)).toBe('fr');
  });

  it('caches session and treats 401 as offline', async () => {
    writeCachedSession(demoSession({ companyName: 'Cached' }));
    expect(readCachedSession()?.companyName).toBe('Cached');
    expect(readCachedSession()?.fromCache).toBe(true);

    const unauthorized = await fetchPosSession(
      vi.fn().mockResolvedValue({ status: 401, ok: false }),
      place,
    );
    expect(unauthorized.posEnabled).toBeNull();
    expect(defaultSessionState().posEnabled).toBeNull();

    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        posEnabled: true,
        language: 'fr',
        languageSource: 'USER',
        currency: 'CAD',
        currencySource: 'WMS',
        companyName: 'Maple',
      }),
    });
    const live = await fetchPosSession(fetchImpl, place);
    expect(live.language).toBe('fr');
    expect(live.currency).toBe('CAD');
    expect(readCachedSession()?.companyName).toBe('Maple');
    localStorage.setItem(POS_SESSION_CACHE_KEY, 'not-json');
    expect(readCachedSession()).toBeNull();
  });

  it('throws when the session API fails', async () => {
    await expect(
      fetchPosSession(vi.fn().mockResolvedValue({ ok: false, status: 500 }), place),
    ).rejects.toThrow(/HTTP 500/);
  });
});
