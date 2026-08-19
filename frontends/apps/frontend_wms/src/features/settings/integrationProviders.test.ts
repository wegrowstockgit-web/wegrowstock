import { describe, expect, it } from 'vitest';
import { hubStatusLabel, providerMeta } from './integrationProviders';

describe('integrationProviders', () => {
  it('returns catalog metadata and status pills', () => {
    expect(providerMeta('quickbooks').label).toBe('QuickBooks');
    expect(providerMeta('UNKNOWN').signupUrl).toContain('stripe');
    expect(hubStatusLabel('LIVE')).toBe('LIVE');
    expect(hubStatusLabel('ACTION_REQUIRED', true)).toBe('ACTION REQUIRED / TOKEN EXPIRED');
    expect(hubStatusLabel('ACTION_REQUIRED', false)).toBe('ACTION REQUIRED');
    expect(hubStatusLabel('DISCONNECTED')).toBe('DISCONNECTED');
  });
});
