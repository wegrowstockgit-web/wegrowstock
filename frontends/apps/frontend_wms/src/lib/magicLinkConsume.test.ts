import { afterEach, describe, expect, it } from 'vitest';
import { claimMagicLinkToken, postLoginPath, resetMagicLinkClaimsForTests } from './magicLinkConsume';

describe('magicLinkConsume', () => {
  afterEach(() => {
    resetMagicLinkClaimsForTests();
  });

  it('claims a token only once', () => {
    expect(claimMagicLinkToken('abc')).toBe(true);
    expect(claimMagicLinkToken('abc')).toBe(false);
    expect(claimMagicLinkToken('def')).toBe(true);
  });

  it('ignores blank tokens', () => {
    expect(claimMagicLinkToken('')).toBe(false);
    expect(claimMagicLinkToken('  ')).toBe(false);
    expect(claimMagicLinkToken(null)).toBe(false);
  });

  it('routes exclusive roles to their home', () => {
    expect(postLoginPath(['OWNER'])).toBe('/dashboard');
    expect(postLoginPath(['PICKER'])).toBe('/fulfillment');
    expect(postLoginPath(['B2B_CUSTOMER'])).toBe('/showroom/catalog');
    expect(postLoginPath(['OWNER', 'PICKER'])).toBe('/dashboard');
  });
});
