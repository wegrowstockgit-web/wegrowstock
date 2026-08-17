/** Survives React Strict Mode remounts so a one-time magic token is POSTed once. */
const claimedTokens = new Set<string>();

export function claimMagicLinkToken(token: string | null | undefined): boolean {
  const value = token?.trim() ?? '';
  if (!value || claimedTokens.has(value)) {
    return false;
  }
  claimedTokens.add(value);
  return true;
}

export function resetMagicLinkClaimsForTests(): void {
  claimedTokens.clear();
}

export function postLoginPath(roles: string[] | undefined): string {
  if (roles && roles.length > 0 && roles.every((role) => role === 'B2B_CUSTOMER')) {
    return '/showroom/catalog';
  }
  if (roles && roles.length > 0 && roles.every((role) => role === 'PICKER')) {
    return '/fulfillment';
  }
  return '/dashboard';
}
