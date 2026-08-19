import { describe, expect, it } from 'vitest';
import {
  buildWmsImpersonationUrl,
  resolveImpersonationHandoff,
  resolveImpersonationRedirectUrl,
  wmsImpersonationRedirectHref,
} from './api';

describe('impersonation URL helpers', () => {
  it('prefers handoffToken and redirectUrl aliases', () => {
    const href = wmsImpersonationRedirectHref({
      accessToken: 'jwt',
      handoffCode: 'legacy',
      handoffToken: 'token-9',
      expiresInSeconds: 900,
      loginUrl: 'http://localhost:3000/login?impersonateCode=legacy',
      redirectUrl: 'http://localhost:3000/login',
      email: 'owner@acme.test',
    });
    expect(href).toBe('http://localhost:3000/login?handoff=token-9');
    expect(resolveImpersonationHandoff({
      accessToken: 'jwt',
      handoffCode: 'legacy',
      handoffToken: 'token-9',
      expiresInSeconds: 900,
      loginUrl: '',
      email: '',
    })).toBe('token-9');
    expect(resolveImpersonationRedirectUrl({
      accessToken: 'jwt',
      handoffCode: 'legacy',
      expiresInSeconds: 900,
      loginUrl: 'http://localhost:3000/login?handoff=legacy',
      email: '',
    })).toBe('http://localhost:3000/login');
  });

  it('builds ?handoff= from the WMS login base', () => {
    expect(buildWmsImpersonationUrl('abc', undefined, 'http://localhost:3000/login'))
      .toBe('http://localhost:3000/login?handoff=abc');
  });
});
