export type HrdResponse = {
  tenantId?: string | null;
  ssoType?: string | null;
  ssoUrl?: string | null;
  isPasswordAllowed: boolean;
  companyName?: string | null;
};

export type HrdStep = 'email' | 'password' | 'sso-optional' | 'sso-redirect';

export function nextHrdStep(res: HrdResponse | null | undefined): HrdStep {
  if (!res) return 'password';
  if (res.ssoUrl && res.isPasswordAllowed === false) return 'sso-redirect';
  if (res.ssoUrl && res.isPasswordAllowed) return 'sso-optional';
  return 'password';
}

export function resolveSsoHref(ssoUrl: string, apiBase = ''): string {
  if (ssoUrl.startsWith('http://') || ssoUrl.startsWith('https://')) {
    return ssoUrl;
  }
  return `${apiBase}${ssoUrl}`;
}
