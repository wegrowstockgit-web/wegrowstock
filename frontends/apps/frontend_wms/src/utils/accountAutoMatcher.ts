import type { LedgerAccount } from '@/api/types';

export const REQUIRED_ACCOUNT_TYPES = ['INVENTORY_ASSET', 'COGS', 'SALES_REVENUE', 'TAX'] as const;
export type RequiredAccountType = (typeof REQUIRED_ACCOUNT_TYPES)[number];

const KEYWORDS: Record<RequiredAccountType, string[]> = {
  INVENTORY_ASSET: ['inventory', 'stock', 'raw materials', 'asset'],
  COGS: ['cogs', 'cost of goods', 'cost of sales'],
  SALES_REVENUE: ['sales', 'revenue', 'income'],
  TAX: ['tax', 'vat', 'iva', 'sales tax'],
};

const PREFERRED_CODES: Partial<Record<RequiredAccountType, string>> = {
  INVENTORY_ASSET: '12000',
  COGS: '50000',
  SALES_REVENUE: '40000',
  TAX: '22000',
};

export function haystack(account: LedgerAccount): string {
  return [account.name, account.code, account.type, account.classification]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

export function scoreAccount(account: LedgerAccount, requiredType: string): number {
  const type = requiredType as RequiredAccountType;
  const keywords = KEYWORDS[type] ?? [];
  const text = haystack(account);
  let score = 0;
  if (PREFERRED_CODES[type] && account.code === PREFERRED_CODES[type]) {
    score += 50;
  }
  for (const keyword of keywords) {
    if (text.includes(keyword)) {
      score += keyword.length;
    }
  }
  return score;
}

/**
 * Fuzzy-match provider ledger accounts onto required WMS mapping types
 * so the wizard can pre-populate dropdowns.
 */
export function suggestAccountMappings(
  availableAccounts: LedgerAccount[],
  requiredTypes: readonly string[] = REQUIRED_ACCOUNT_TYPES,
): Record<string, string> {
  const suggestions: Record<string, string> = {};
  const used = new Set<string>();
  for (const requiredType of requiredTypes) {
    let best: LedgerAccount | undefined;
    let bestScore = 0;
    for (const account of availableAccounts) {
      if (used.has(account.accountId)) continue;
      const score = scoreAccount(account, requiredType);
      if (score > bestScore) {
        best = account;
        bestScore = score;
      }
    }
    if (best && bestScore > 0) {
      suggestions[requiredType] = best.accountId;
      used.add(best.accountId);
    }
  }
  return suggestions;
}
