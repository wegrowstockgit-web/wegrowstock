import { normalizePosLanguage, type PosLanguage } from './i18n';
import type { TaxRegion } from './tax';

export type PosCurrency = string;

export type PlaceContext = {
  language: PosLanguage;
  currency: string;
  timezone: string;
  localeTag: string;
  taxRegion: TaxRegion;
};

export type ResolvedPosLocale = {
  language: PosLanguage;
  languageSource: 'ORGANIZATION' | 'USER' | 'PLACE' | 'DEFAULT';
  currency: string;
  currencySource: 'WMS' | 'PLACE' | 'DEFAULT';
  placeLanguage: PosLanguage;
  placeCurrency: string;
  localeTag: string;
  taxRegion: TaxRegion;
  timezone: string;
};

const CURRENCY_BY_LOCALE: Record<string, string> = {
  mx: 'MXN',
  gb: 'GBP',
  uk: 'GBP',
  fr: 'EUR',
  de: 'EUR',
  es: 'EUR',
  it: 'EUR',
  ca: 'CAD',
  au: 'AUD',
};

export function detectPlace(now: () => PlaceContext = detectBrowserPlace): PlaceContext {
  return now();
}

export function detectBrowserPlace(): PlaceContext {
  const languageTag =
    typeof navigator !== 'undefined' ? navigator.language || navigator.languages?.[0] || 'en-US' : 'en-US';
  const timezone =
    typeof Intl !== 'undefined'
      ? Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
      : 'UTC';
  const language = normalizePosLanguage(languageTag);
  const currency = inferPlaceCurrency(languageTag, timezone);
  const taxRegion = inferTaxRegion(languageTag, timezone, currency);
  return {
    language,
    currency,
    timezone,
    localeTag: languageTag,
    taxRegion,
  };
}

export function inferPlaceCurrency(languageTag: string, timezone: string): string {
  const zone = timezone || '';
  if (zone.startsWith('America/Mexico') || zone === 'America/Tijuana' || zone === 'America/Cancun') {
    return 'MXN';
  }
  if (zone.startsWith('Europe/London') || zone === 'Europe/Belfast') return 'GBP';
  if (zone.startsWith('Europe/')) return 'EUR';
  if (zone.startsWith('America/Toronto') || zone.startsWith('America/Vancouver')) return 'CAD';
  if (zone.startsWith('Australia/')) return 'AUD';

  const tag = languageTag.toLowerCase();
  const region = tag.split('-')[1];
  if (region && CURRENCY_BY_LOCALE[region]) return CURRENCY_BY_LOCALE[region];
  if (tag.startsWith('fr') || tag.startsWith('es')) return 'EUR';
  return 'USD';
}

export function inferTaxRegion(languageTag: string, timezone: string, placeCurrency: string): TaxRegion {
  if (timezone.includes('Mexico') || timezone === 'America/Tijuana' || timezone === 'America/Cancun') {
    return 'MX';
  }
  if (placeCurrency === 'MXN' || languageTag.toLowerCase().includes('mx')) return 'MX';
  return 'US';
}

export function moneyLocale(language: PosLanguage, currency: string, taxRegion: TaxRegion): string {
  if (taxRegion === 'MX' || currency === 'MXN') return language === 'es' ? 'es-MX' : `${language}-MX`;
  if (currency === 'GBP') return language === 'en' ? 'en-GB' : `${language}-GB`;
  if (currency === 'CAD') return language === 'fr' ? 'fr-CA' : 'en-CA';
  if (currency === 'EUR' && language === 'fr') return 'fr-FR';
  if (language === 'es') return 'es-ES';
  if (language === 'fr') return 'fr-FR';
  return 'en-US';
}

export function cashPresets(currency: string): [number, number, number] {
  if (currency === 'GBP') return [10, 20, 50];
  return [20, 50, 100];
}

export function resolveOfflineLocale(place: PlaceContext = detectPlace()): ResolvedPosLocale {
  return {
    language: place.language,
    languageSource: 'PLACE',
    currency: place.currency,
    currencySource: 'PLACE',
    placeLanguage: place.language,
    placeCurrency: place.currency,
    localeTag: moneyLocale(place.language, place.currency, place.taxRegion),
    taxRegion: place.taxRegion,
    timezone: place.timezone,
  };
}
