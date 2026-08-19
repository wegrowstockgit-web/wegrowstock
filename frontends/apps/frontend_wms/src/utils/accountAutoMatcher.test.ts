import { describe, expect, it } from 'vitest';
import type { LedgerAccount } from '@/api/types';
import {
  REQUIRED_ACCOUNT_TYPES,
  haystack,
  scoreAccount,
  suggestAccountMappings,
} from './accountAutoMatcher';

const accounts: LedgerAccount[] = [
  { accountId: 'inv-1', name: 'Inventory Asset', type: 'Other Current Asset', classification: 'Asset', code: '12000' },
  { accountId: 'cogs-1', name: 'Cost of Goods Sold', type: 'COGS', classification: 'Expense', code: '50000' },
  { accountId: 'rev-1', name: 'Sales Revenue', type: 'Income', classification: 'Revenue', code: '40000' },
  { accountId: 'tax-1', name: 'Sales Tax Payable', type: 'Other Current Liability', classification: 'Liability', code: '22000' },
  { accountId: 'bank', name: 'Business Bank', type: 'Bank', classification: 'Asset', code: '10000' },
];

describe('accountAutoMatcher', () => {
  it('pre-populates required mapping types from fuzzy keywords', () => {
    const suggestions = suggestAccountMappings(accounts, REQUIRED_ACCOUNT_TYPES);
    expect(suggestions.INVENTORY_ASSET).toBe('inv-1');
    expect(suggestions.COGS).toBe('cogs-1');
    expect(suggestions.SALES_REVENUE).toBe('rev-1');
    expect(suggestions.TAX).toBe('tax-1');
  });

  it('matches stock / raw materials / vat synonyms and ignores bank', () => {
    const alt: LedgerAccount[] = [
      { accountId: 'stock', name: 'Raw Materials Stock', type: 'Asset', classification: 'Asset', code: '' },
      { accountId: 'cos', name: 'Cost of Sales', type: 'Expense', classification: 'Expense', code: '' },
      { accountId: 'inc', name: 'Trading Income', type: 'Income', classification: 'Revenue', code: '' },
      { accountId: 'vat', name: 'VAT Control', type: 'Liability', classification: 'Liability', code: '' },
      { accountId: 'bank', name: 'Business Bank', type: 'Bank', classification: 'Asset', code: '10000' },
    ];
    const suggestions = suggestAccountMappings(alt);
    expect(suggestions.INVENTORY_ASSET).toBe('stock');
    expect(suggestions.COGS).toBe('cos');
    expect(suggestions.SALES_REVENUE).toBe('inc');
    expect(suggestions.TAX).toBe('vat');
    expect(Object.values(suggestions)).not.toContain('bank');
  });

  it('scores preferred codes highest and builds a haystack', () => {
    expect(haystack(accounts[0])).toContain('inventory');
    expect(scoreAccount(accounts[0], 'INVENTORY_ASSET')).toBeGreaterThan(
      scoreAccount(accounts[4], 'INVENTORY_ASSET'),
    );
    expect(suggestAccountMappings([], REQUIRED_ACCOUNT_TYPES)).toEqual({});
  });
});
