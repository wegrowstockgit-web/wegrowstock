export type CidrEntry = {
  cidr: string;
  label: string;
  raw: string;
};

export function parseCidrEntry(raw: string | null | undefined): CidrEntry {
  const value = (raw ?? '').trim();
  const hash = value.indexOf('#');
  if (hash < 0) {
    return { cidr: value, label: '', raw: value };
  }
  return {
    cidr: value.slice(0, hash).trim(),
    label: value.slice(hash + 1).trim(),
    raw: value,
  };
}

export function formatCidrEntry(cidr: string, label?: string | null): string {
  const block = cidr.trim();
  const name = (label ?? '').trim().replace(/#/g, ' ');
  return name ? `${block}#${name}` : block;
}

export function ipv4ToInt(ip: string): number | null {
  const parts = ip.split('.');
  if (parts.length !== 4) return null;
  let value = 0;
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) return null;
    const octet = Number(part);
    if (octet > 255) return null;
    value = (value << 8) + octet;
  }
  return value >>> 0;
}

export function ipInCidr(ip: string, cidrOrEntry: string): boolean {
  if (!ip || ip === 'unknown') return false;
  const { cidr } = parseCidrEntry(cidrOrEntry);
  const [base, bitsRaw] = cidr.split('/');
  if (!base) return false;
  const ipInt = ipv4ToInt(ip);
  const baseInt = ipv4ToInt(base);
  if (ipInt != null && baseInt != null) {
    const bits = bitsRaw == null || bitsRaw === '' ? 32 : Number(bitsRaw);
    if (!Number.isInteger(bits) || bits < 0 || bits > 32) return false;
    if (bits === 0) return true;
    const mask = bits === 32 ? 0xffffffff : (~((1 << (32 - bits)) - 1)) >>> 0;
    return (ipInt & mask) === (baseInt & mask);
  }
  return ip.toLowerCase() === base.toLowerCase();
}

export function clientIpCovered(ip: string | undefined, entries: string[]): boolean {
  if (!ip || entries.length === 0) return false;
  return entries.some((entry) => ipInCidr(ip, entry));
}

export const NETWORK_ACCESS_LEVELS = [
  'STRICT_INTERNAL',
  'MFA_OUTSIDE_NETWORK',
  'ROAMING',
] as const;

export type NetworkAccessLevel = (typeof NETWORK_ACCESS_LEVELS)[number];

export const NETWORK_ACCESS_LABELS: Record<NetworkAccessLevel, string> = {
  STRICT_INTERNAL: 'Internal Only',
  MFA_OUTSIDE_NETWORK: 'MFA Remote',
  ROAMING: 'Roaming',
};

export function parseNetworkAccessLevel(raw: string | null | undefined): NetworkAccessLevel {
  if (raw === 'MFA_OUTSIDE_NETWORK' || raw === 'ROAMING' || raw === 'STRICT_INTERNAL') {
    return raw;
  }
  return 'STRICT_INTERNAL';
}

export function isMfaRequiredTitle(title: string | undefined | null): boolean {
  return title === 'MFA_REQUIRED_FOR_EXTERNAL_ACCESS';
}

/** SHA-256 hex — mirrors TerminalBiometricService.computeAssertionSignature. */
export async function sha256Hex(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function computeAssertionSignature(
  challenge: string,
  credentialId: string,
  secret: string,
): Promise<string> {
  const secretHash = await sha256Hex(secret);
  return sha256Hex(`${challenge}:${credentialId}:${secretHash}`);
}

export type MfaChallengeBody = {
  title?: string;
  challenge?: string;
  timeout?: number;
  rpId?: string;
  allowCredentials?: Array<{ id?: string; type?: string }>;
  userId?: string;
};

export type MfaAssertion = {
  mfaCredentialId: string;
  mfaChallenge: string;
  mfaSignature: string;
};

/**
 * Prefer WebAuthn, then a stored software passkey (same HMAC protocol as terminal biometrics).
 */
export async function completeMfaAssertion(
  body: MfaChallengeBody,
  stored?: { credentialId: string; secret: string } | null,
): Promise<MfaAssertion> {
  const challenge = body.challenge ?? '';
  if (!challenge) {
    throw new Error('Missing MFA challenge');
  }

  const webAuthn = typeof navigator !== 'undefined' ? navigator.credentials : undefined;
  if (webAuthn && typeof webAuthn.get === 'function') {
    try {
      const allowIds = (body.allowCredentials ?? [])
        .map((row) => row.id)
        .filter((id): id is string => Boolean(id));
      const timeout = stored?.secret ? Math.min(body.timeout ?? 4_000, 4_000) : (body.timeout ?? 120_000);
      const cred = (await webAuthn.get({
        publicKey: {
          challenge: new TextEncoder().encode(challenge),
          timeout,
          rpId: body.rpId,
          userVerification: 'preferred',
          allowCredentials: allowIds.map((id) => ({
            type: 'public-key',
            id: new TextEncoder().encode(id),
          })),
        },
      } as CredentialRequestOptions)) as { id?: string; rawId?: ArrayBuffer } | null;
      const credentialId = cred?.id || allowIds[0];
      if (credentialId && stored?.secret && stored.credentialId === credentialId) {
        return {
          mfaCredentialId: credentialId,
          mfaChallenge: challenge,
          mfaSignature: await computeAssertionSignature(challenge, credentialId, stored.secret),
        };
      }
      if (credentialId && stored?.secret) {
        return {
          mfaCredentialId: stored.credentialId,
          mfaChallenge: challenge,
          mfaSignature: await computeAssertionSignature(challenge, stored.credentialId, stored.secret),
        };
      }
    } catch {
      // Fall through to software assertion.
    }
  }

  if (stored?.credentialId && stored.secret) {
    return {
      mfaCredentialId: stored.credentialId,
      mfaChallenge: challenge,
      mfaSignature: await computeAssertionSignature(challenge, stored.credentialId, stored.secret),
    };
  }

  const fallbackId = body.allowCredentials?.[0]?.id;
  if (fallbackId && stored?.secret) {
    return {
      mfaCredentialId: fallbackId,
      mfaChallenge: challenge,
      mfaSignature: await computeAssertionSignature(challenge, fallbackId, stored.secret),
    };
  }

  throw new Error('Passkey required for off-network access');
}
