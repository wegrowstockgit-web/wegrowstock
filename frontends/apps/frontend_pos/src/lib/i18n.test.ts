import { describe, expect, it } from 'vitest';
import { normalizePosLanguage, translate } from './i18n';

describe('pos i18n', () => {
  it('normalizes language prefixes', () => {
    expect(normalizePosLanguage('es-MX')).toBe('es');
    expect(normalizePosLanguage('fr_CA')).toBe('fr');
    expect(normalizePosLanguage('de')).toBe('en');
    expect(normalizePosLanguage(null)).toBe('en');
  });

  it('interpolates cashier copy in all three languages', () => {
    expect(translate('en', 'register.unknownUpc', { upc: '000' })).toBe('Unknown UPC 000');
    expect(translate('es', 'register.scanFirst')).toContain('artículo');
    expect(translate('fr', 'locked.title')).toContain('POS');
    expect(translate('en', 'register.success')).toBe('SUCCESS - NEXT CUSTOMER');
  });
});
