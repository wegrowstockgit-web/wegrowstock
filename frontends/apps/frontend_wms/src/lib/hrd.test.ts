import { describe, expect, it } from 'vitest';
import { nextHrdStep, resolveSsoHref } from './hrd';

describe('home realm discovery routing', () => {
  it('redirects when SSO is enforced', () => {
    expect(
      nextHrdStep({
        isPasswordAllowed: false,
        ssoUrl: '/oauth2/authorization/t1',
        companyName: 'Acme',
      }),
    ).toBe('sso-redirect');
  });

  it('offers SSO plus password when both are allowed', () => {
    expect(
      nextHrdStep({
        isPasswordAllowed: true,
        ssoUrl: '/saml2/authenticate/t1',
        ssoType: 'SAML',
        companyName: 'Acme',
      }),
    ).toBe('sso-optional');
  });

  it('falls back to password when there is no realm', () => {
    expect(nextHrdStep({ isPasswordAllowed: true })).toBe('password');
    expect(nextHrdStep(null)).toBe('password');
    expect(resolveSsoHref('/oauth2/authorization/t1', 'http://localhost:8080')).toBe(
      'http://localhost:8080/oauth2/authorization/t1',
    );
    expect(resolveSsoHref('https://idp.example/sso')).toBe('https://idp.example/sso');
  });
});
