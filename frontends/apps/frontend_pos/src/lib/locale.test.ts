import { describe, expect, it } from 'vitest';
import {
  cashPresets,
  inferPlaceCurrency,
  inferTaxRegion,
  moneyLocale,
  resolveOfflineLocale,
} from './locale';

describe('pos locale', () => {
  it('infers place currency and tax from timezone and language', () => {
    expect(inferPlaceCurrency('en-US', 'America/Mexico_City')).toBe('MXN');
    expect(inferPlaceCurrency('en-GB', 'Europe/London')).toBe('GBP');
    expect(inferPlaceCurrency('fr-FR', 'Europe/Paris')).toBe('EUR');
    expect(inferPlaceCurrency('en-CA', 'America/Toronto')).toBe('CAD');
    expect(inferPlaceCurrency('en-AU', 'Australia/Sydney')).toBe('AUD');
    expect(inferPlaceCurrency('es-ES', 'UTC')).toBe('EUR');
    expect(inferPlaceCurrency('en-US', 'America/New_York')).toBe('USD');
    expect(inferTaxRegion('es-MX', 'America/New_York', 'USD')).toBe('MX');
    expect(inferTaxRegion('en-US', 'America/Mexico_City', 'USD')).toBe('MX');
    expect(inferTaxRegion('en-US', 'America/New_York', 'USD')).toBe('US');
  });

  it('picks cash presets and money locales', () => {
    expect(cashPresets('GBP')).toEqual([10, 20, 50]);
    expect(cashPresets('EUR')).toEqual([20, 50, 100]);
    expect(moneyLocale('es', 'MXN', 'MX')).toBe('es-MX');
    expect(moneyLocale('fr', 'EUR', 'US')).toBe('fr-FR');
    expect(moneyLocale('en', 'GBP', 'US')).toBe('en-GB');
    expect(moneyLocale('fr', 'CAD', 'US')).toBe('fr-CA');
    expect(moneyLocale('en', 'USD', 'US')).toBe('en-US');
  });

  it('builds an offline locale from a place', () => {
    const resolved = resolveOfflineLocale({
      language: 'fr',
      currency: 'EUR',
      timezone: 'Europe/Paris',
      localeTag: 'fr-FR',
      taxRegion: 'US',
    });
    expect(resolved.languageSource).toBe('PLACE');
    expect(resolved.currency).toBe('EUR');
    expect(resolved.localeTag).toBe('fr-FR');
  });
});
