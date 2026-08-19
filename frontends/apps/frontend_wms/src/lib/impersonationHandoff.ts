/** Survives React Strict Mode remounts so a one-time handoff is POSTed once. */
const claimedCodes = new Set<string>();

export function readImpersonationHandoff(
  search: URLSearchParams | { get(name: string): string | null },
): string | null {
  const value =
    search.get('handoff')
    || search.get('impersonateCode')
    || search.get('impersonateToken');
  const trimmed = value?.trim() ?? '';
  return trimmed || null;
}

export function claimImpersonationHandoff(code: string | null | undefined): boolean {
  const value = code?.trim() ?? '';
  if (!value || claimedCodes.has(value)) {
    return false;
  }
  claimedCodes.add(value);
  return true;
}

export function resetImpersonationHandoffClaimsForTests(): void {
  claimedCodes.clear();
}
