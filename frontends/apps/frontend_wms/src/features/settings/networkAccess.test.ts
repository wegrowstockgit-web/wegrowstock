import { describe, expect, it, vi } from 'vitest';
import {
  clientIpCovered,
  completeMfaAssertion,
  computeAssertionSignature,
  formatCidrEntry,
  ipInCidr,
  isMfaRequiredTitle,
  parseCidrEntry,
  parseNetworkAccessLevel,
} from './networkAccess';

describe('networkAccess helpers', () => {
  it('parses levels and MFA title', () => {
    expect(parseNetworkAccessLevel('ROAMING')).toBe('ROAMING');
    expect(parseNetworkAccessLevel('nope')).toBe('STRICT_INTERNAL');
    expect(isMfaRequiredTitle('MFA_REQUIRED_FOR_EXTERNAL_ACCESS')).toBe(true);
    expect(isMfaRequiredTitle('INVALID_CREDENTIALS')).toBe(false);
  });

  it('computes HMAC-style assertion and uses stored passkey', async () => {
    const sig = await computeAssertionSignature('chal', 'cred_1', 'secret');
    expect(sig).toMatch(/^[0-9a-f]{64}$/);

    const assertion = await completeMfaAssertion(
      { challenge: 'chal', allowCredentials: [{ id: 'cred_1' }] },
      { credentialId: 'cred_1', secret: 'secret' },
    );
    expect(assertion.mfaCredentialId).toBe('cred_1');
    expect(assertion.mfaChallenge).toBe('chal');
    expect(assertion.mfaSignature).toBe(sig);
  });

  it('prefers WebAuthn get then stored secret', async () => {
    const get = vi.fn().mockResolvedValue({ id: 'cred_1' });
    vi.stubGlobal('navigator', { credentials: { get } });
    const assertion = await completeMfaAssertion(
      { challenge: 'chal', rpId: 'invsys.local', allowCredentials: [{ id: 'cred_1' }] },
      { credentialId: 'cred_1', secret: 'secret' },
    );
    expect(get).toHaveBeenCalled();
    expect(assertion.mfaCredentialId).toBe('cred_1');
    vi.unstubAllGlobals();
  });

  it('uses stored credential when WebAuthn id differs, then fallbacks and errors', async () => {
    await expect(completeMfaAssertion({} as never)).rejects.toThrow('Missing MFA challenge');

    const getMismatch = vi.fn().mockResolvedValue({ id: 'other' });
    vi.stubGlobal('navigator', { credentials: { get: getMismatch } });
    const mismatched = await completeMfaAssertion(
      { challenge: 'chal', allowCredentials: [{ id: 'other' }] },
      { credentialId: 'cred_1', secret: 'secret' },
    );
    expect(mismatched.mfaCredentialId).toBe('cred_1');
    vi.unstubAllGlobals();

    const getFail = vi.fn().mockRejectedValue(new Error('no authenticator'));
    vi.stubGlobal('navigator', { credentials: { get: getFail } });
    const fallback = await completeMfaAssertion(
      { challenge: 'chal', allowCredentials: [{ id: 'cred_2' }] },
      { credentialId: 'cred_2', secret: 'secret' },
    );
    expect(fallback.mfaCredentialId).toBe('cred_2');
    vi.unstubAllGlobals();

    vi.stubGlobal('navigator', { credentials: { get: vi.fn().mockRejectedValue(new Error('fail')) } });
    const fromAllowList = await completeMfaAssertion(
      { challenge: 'chal', allowCredentials: [{ id: 'cred_3' }] },
      { credentialId: '', secret: 'secret' },
    );
    expect(fromAllowList.mfaCredentialId).toBe('cred_3');
    vi.unstubAllGlobals();

    await expect(
      completeMfaAssertion({ challenge: 'chal', allowCredentials: [] }, null),
    ).rejects.toThrow('Passkey required');
  });

  it('parses labeled CIDRs and matches IPv4 coverage', () => {
    expect(parseCidrEntry('10.0.0.0/8#Dallas Warehouse')).toEqual({
      cidr: '10.0.0.0/8',
      label: 'Dallas Warehouse',
      raw: '10.0.0.0/8#Dallas Warehouse',
    });
    expect(formatCidrEntry('198.51.100.45/32', 'Main Office Wi-Fi')).toBe(
      '198.51.100.45/32#Main Office Wi-Fi',
    );
    expect(ipInCidr('10.9.1.4', '10.0.0.0/8#Dallas Warehouse')).toBe(true);
    expect(ipInCidr('198.51.100.45', '10.0.0.0/8')).toBe(false);
    expect(clientIpCovered('198.51.100.45', ['10.0.0.0/8'])).toBe(false);
    expect(clientIpCovered('10.1.2.3', ['10.0.0.0/8'])).toBe(true);
  });
});
