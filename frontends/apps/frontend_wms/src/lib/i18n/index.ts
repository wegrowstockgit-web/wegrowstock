import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import es from './locales/es.json';
import fr from './locales/fr.json';

export const SUPPORTED_LANGUAGES = ['en', 'es', 'fr'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/** Explicit language cookie/localStorage key used across login restore and settings. */
export const WMS_LANG_STORAGE_KEY = 'wms_lang';

export function normalizeLanguage(raw?: string | null): SupportedLanguage {
  if (!raw) return 'en';
  const token = raw.trim().toLowerCase().replace('_', '-');
  if (token.startsWith('es')) return 'es';
  if (token.startsWith('fr')) return 'fr';
  return 'en';
}

export function readStoredLanguage(): SupportedLanguage {
  if (typeof localStorage === 'undefined') return 'en';
  return normalizeLanguage(localStorage.getItem(WMS_LANG_STORAGE_KEY));
}

export function persistLanguage(lang: SupportedLanguage): void {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(WMS_LANG_STORAGE_KEY, lang);
  }
  void i18n.changeLanguage(lang);
}

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    es: { translation: es },
    fr: { translation: fr },
  },
  lng: readStoredLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

i18n.on('languageChanged', (lng) => {
  const normalized = normalizeLanguage(lng);
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(WMS_LANG_STORAGE_KEY, normalized);
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = normalized;
  }
});

export default i18n;
