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
