import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import es from './locales/es.json';
import fr from './locales/fr.json';

export const SUPPORTED_LANGUAGES = ['en', 'es', 'fr'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

export function normalizeLanguage(raw?: string | null): SupportedLanguage {
  if (!raw) return 'en';
  const token = raw.trim().toLowerCase().replace('_', '-');
  if (token.startsWith('es')) return 'es';
  if (token.startsWith('fr')) return 'fr';
  return 'en';
}

void i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    es: { translation: es },
    fr: { translation: fr },
  },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

i18n.on('languageChanged', (lng) => {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = lng;
  }
});

export default i18n;
