import { describe, expect, it } from 'vitest';
import i18n, { normalizeLanguage, persistLanguage, readStoredLanguage, WMS_LANG_STORAGE_KEY } from './index';
import en from './locales/en.json';
import es from './locales/es.json';
import fr from './locales/fr.json';

function collectKeys(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return prefix ? [prefix] : [];
  }
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    collectKeys(child, prefix ? `${prefix}.${key}` : key),
  );
}

describe('i18n language normalization', () => {
  it('reads and persists wms_lang', async () => {
    localStorage.setItem(WMS_LANG_STORAGE_KEY, 'fr-CA');
    expect(readStoredLanguage()).toBe('fr');
    persistLanguage('es');
    expect(localStorage.getItem(WMS_LANG_STORAGE_KEY)).toBe('es');
    expect(i18n.language).toMatch(/^es/);
    persistLanguage('en');
    await i18n.changeLanguage('en');
  });

  it('maps locale tags onto en/es/fr', () => {
    expect(normalizeLanguage('en-US')).toBe('en');
    expect(normalizeLanguage('es-MX')).toBe('es');
    expect(normalizeLanguage('fr-CA')).toBe('fr');
    expect(normalizeLanguage(null)).toBe('en');
  });
});

describe('locale catalogs', () => {
  it('keeps English, Spanish, and French key trees in parity', () => {
    const enKeys = collectKeys(en).sort();
    expect(collectKeys(es).sort()).toEqual(enKeys);
    expect(collectKeys(fr).sort()).toEqual(enKeys);
  });

  it('exposes copilot chrome and role labels in all three languages', () => {
    for (const catalog of [en, es, fr]) {
      expect(catalog.chat.title).toBeTruthy();
      expect(catalog.chat.askPlaceholder).toBeTruthy();
      expect(catalog.roles.WAREHOUSE_MANAGER).toBeTruthy();
      expect(catalog.pageHelp.playbooks.inventory_ledger.title).toBeTruthy();
      expect(catalog.pageHelp.playbooks.settings_tab_users.title).toBeTruthy();
    }
    expect(es.chat.title).not.toEqual(en.chat.title);
    expect(fr.chat.title).not.toEqual(en.chat.title);
  });

  it('switches live i18n instance across en/es/fr', async () => {
    try {
      await i18n.changeLanguage('es');
      expect(i18n.t('nav.Dashboard')).toBe('Panel');
      expect(i18n.t('nav.meshNetwork')).toBe('Red Mesh');
      expect(i18n.t('settings.workspaceLanguageUpdated')).toMatch(/actualizado/i);
      expect(i18n.t('pageHelp.playbooks.dashboard.title')).toMatch(/mando/i);
      await i18n.changeLanguage('fr');
      expect(i18n.t('nav.Dashboard')).toBe('Tableau de bord');
      expect(i18n.t('nav.meshNetwork')).toBe('Réseau Mesh');
      expect(i18n.t('chat.title')).toMatch(/Copilote/i);
    } finally {
      await i18n.changeLanguage('en');
    }
  });

  it('covers navigation, settings, mesh, and sales dictionaries', () => {
    expect(en.nav.meshNetwork).toBe('Mesh Network');
    expect(en.settings.workspaceLanguage).toBeTruthy();
    expect(en.mesh.requestConnection).toBeTruthy();
    expect(en.sales.statuses.PENDING_REP_APPROVAL).toBeTruthy();
    expect(en.sales.acceptQuote).toBeTruthy();
    expect(es.nav.meshNetwork).not.toEqual(en.nav.meshNetwork);
    expect(fr.settings.companyPreferences).not.toEqual(en.settings.companyPreferences);
  });
});
