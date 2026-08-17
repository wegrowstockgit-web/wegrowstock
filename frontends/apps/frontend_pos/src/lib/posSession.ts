import { normalizePosLanguage, type PosLanguage } from './i18n';
import {
  detectPlace,
  inferTaxRegion,
  moneyLocale,
  resolveOfflineLocale,
  type ResolvedPosLocale,
} from './locale';
import type { TaxRegion } from './tax';
import { syncManagerPinVault } from '@/offline/pinVault';

export const POS_SESSION_CACHE_KEY = 'pos.session.v1';

export type PosSessionDto = {
  posEnabled: boolean;
  module?: string;
  tier?: string;
  language?: string;
  languageSource?: string;
  currency?: string;
  currencySource?: string;
  placeLanguage?: string | null;
  placeCurrency?: string | null;
  localeTag?: string;
  taxRegionHint?: string;
  timezone?: string | null;
  companyName?: string;
  cashierId?: string | null;
  tenantId?: string | null;
};

export type PosSessionState = ResolvedPosLocale & {
  posEnabled: boolean | null;
  companyName: string;
  tier: string;
  cashierId: string;
  tenantId: string;
  fromCache: boolean;
};

export function defaultSessionState(): PosSessionState {
  const offline = resolveOfflineLocale();
  return {
    ...offline,
    posEnabled: null,
    companyName: '',
    tier: '',
    cashierId: '',
    tenantId: '',
    fromCache: false,
  };
}

export function demoSession(overrides: Partial<PosSessionState> = {}): PosSessionState {
  return {
    posEnabled: true,
    language: 'en',
    languageSource: 'ORGANIZATION',
    currency: 'USD',
    currencySource: 'WMS',
    placeLanguage: 'en',
    placeCurrency: 'USD',
    localeTag: 'en-US',
    taxRegion: 'US',
    timezone: 'America/New_York',
    companyName: 'Demo Corp',
    tier: 'ENTERPRISE',
    cashierId: 'a0000000-0000-4000-8000-000000000201',
    tenantId: 'a0000000-0000-4000-8000-000000000001',
    fromCache: false,
    ...overrides,
  };
}

export function applySessionDto(dto: PosSessionDto, place = detectPlace()): PosSessionState {
  const entitled = Boolean(dto.posEnabled);
  const language = entitled
    ? normalizePosLanguage(dto.language || place.language)
    : place.language;
  const currency = entitled ? (dto.currency || place.currency).toUpperCase() : place.currency;
  const taxRegion = (dto.taxRegionHint === 'MX' || place.taxRegion === 'MX' ? 'MX' : 'US') as TaxRegion;
  const languageSource: PosSessionState['languageSource'] =
    dto.languageSource === 'ORGANIZATION' ||
    dto.languageSource === 'USER' ||
    dto.languageSource === 'PLACE' ||
    dto.languageSource === 'DEFAULT'
      ? dto.languageSource
      : entitled
        ? 'ORGANIZATION'
        : 'PLACE';
  const currencySource: PosSessionState['currencySource'] =
    dto.currencySource === 'WMS' || dto.currencySource === 'PLACE' || dto.currencySource === 'DEFAULT'
      ? dto.currencySource
      : entitled
        ? 'WMS'
        : 'PLACE';
  return {
    posEnabled: entitled,
    language,
    languageSource,
    currency,
    currencySource,
    placeLanguage: normalizePosLanguage(dto.placeLanguage || place.language),
    placeCurrency: (dto.placeCurrency || place.currency).toUpperCase(),
    localeTag: dto.localeTag || moneyLocale(language, currency, taxRegion),
    taxRegion: entitled ? ((dto.taxRegionHint as TaxRegion) || inferTaxRegion(place.localeTag, place.timezone, place.currency)) : place.taxRegion,
    timezone: dto.timezone || place.timezone,
    companyName: dto.companyName ?? '',
    tier: dto.tier ?? '',
    cashierId: dto.cashierId ?? '',
    tenantId: dto.tenantId ?? '',
    fromCache: false,
  };
}

export function readCachedSession(): PosSessionState | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(POS_SESSION_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PosSessionState;
    if (!parsed || typeof parsed !== 'object') return null;
    return { ...parsed, fromCache: true };
  } catch {
    return null;
  }
}

export function writeCachedSession(state: PosSessionState): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(POS_SESSION_CACHE_KEY, JSON.stringify({ ...state, fromCache: true }));
}

export async function fetchPosSession(
  fetchImpl: typeof fetch = fetch,
  place = detectPlace(),
): Promise<PosSessionState> {
  const params = new URLSearchParams({
    timezone: place.timezone,
    placeLanguage: place.language,
    placeCurrency: place.currency,
  });
  const response = await fetchImpl(`/api/v1/pos/session?${params.toString()}`, {
    credentials: 'include',
    headers: { Accept: 'application/json', 'Accept-Language': place.localeTag },
  });
  if (response.status === 401) {
    return { ...defaultSessionState(), posEnabled: null };
  }
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const dto = (await response.json()) as PosSessionDto;
  const next = applySessionDto(dto, place);
  writeCachedSession(next);
  await syncManagerPinVault(fetchImpl, next.tenantId);
  return next;
}

export function languageForUi(state: PosSessionState): PosLanguage {
  if (state.posEnabled === false) return state.placeLanguage;
  return state.language;
}
