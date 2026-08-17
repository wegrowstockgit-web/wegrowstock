import { rolesInclude } from '@/stores/session';

export const RETAIL_POS_MODULE = 'RETAIL_POS';

export const POS_SETTINGS_KEYS = {
  receiptHeader: 'pos_receipt_header',
  receiptFooter: 'pos_receipt_footer',
  defaultCurrency: 'pos_default_currency',
  requireBlindCloseout: 'pos_require_blind_closeout',
  enableCfdi: 'pos_enable_cfdi_invoicing',
} as const;

export const POS_CURRENCIES = ['USD', 'MXN'] as const;

export type PosCurrency = (typeof POS_CURRENCIES)[number];

export function hasRetailPosModule(modules: readonly string[] | undefined | null): boolean {
  return (modules ?? []).includes(RETAIL_POS_MODULE);
}

/** OWNER/ADMIN plus an explicit RETAIL_POS entitlement — empty modules do not unlock the tab. */
export function canConfigureRetailPos(
  roles: readonly string[] | undefined | null,
  modules: readonly string[] | undefined | null,
): boolean {
  return rolesInclude(roles, 'OWNER', 'ADMIN') && hasRetailPosModule(modules);
}

export function normalizePosCurrency(raw: unknown): PosCurrency {
  const code = String(raw ?? 'USD').trim().toUpperCase();
  return code === 'MXN' ? 'MXN' : 'USD';
}
