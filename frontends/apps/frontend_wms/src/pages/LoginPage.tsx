import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { type AxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@/api/client';
import type { LoginRequest, SessionResponse } from '@/api/types';
import { useSessionStore } from '@/stores/session';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { BrandLogo } from '@/components/layout/BrandLogo';
import { nextHrdStep, resolveSsoHref, type HrdResponse, type HrdStep } from '@/lib/hrd';
import { readTerminalPasskey } from '@/lib/terminalPasskey';
import { claimMagicLinkToken, postLoginPath } from '@/lib/magicLinkConsume';
import {
  claimImpersonationHandoff,
  readImpersonationHandoff,
} from '@/lib/impersonationHandoff';
import {
  completeMfaAssertion,
  isMfaRequiredTitle,
  type MfaChallengeBody,
} from '@/features/settings/networkAccess';

interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  grantedPermissions?: string[];
  isSuperAdmin?: boolean;
  enabledModules?: string[];
  localeLanguage?: string | null;
  tier?: string | null;
}

export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setSessionFromLogin = useSessionStore((s) => s.setSessionFromLogin);
  const applyMeProfile = useSessionStore((s) => s.applyMeProfile);
  const [email, setEmail] = useState('owner@demo.test');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [magicSent, setMagicSent] = useState(false);
  const [step, setStep] = useState<HrdStep>('email');
  const [realm, setRealm] = useState<HrdResponse | null>(null);
  const [mfaChallenge, setMfaChallenge] = useState<MfaChallengeBody | null>(null);
  const [mfaBusy, setMfaBusy] = useState(false);

  useEffect(() => {
    if (searchParams.get('sso') === '1') {
      void (async () => {
        try {
          const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
          applyMeProfile(me.data);
          navigate('/dashboard', { replace: true });
        } catch {
          setError('SSO session could not be established.');
        }
      })();
    }
  }, [searchParams, applyMeProfile, navigate]);

  useEffect(() => {
    const impersonateCode = readImpersonationHandoff(searchParams);
    if (!claimImpersonationHandoff(impersonateCode)) return;
    void (async () => {
      try {
        const session = await apiClient.post<SessionResponse>(
          '/api/v1/auth/impersonation/accept',
          {
            handoff: impersonateCode,
            handoffCode: impersonateCode,
            token: impersonateCode,
          },
        );
        const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
        setSessionFromLogin(session.data, me.data.email, me.data.displayName);
        applyMeProfile(me.data);
        navigate(postLoginPath(me.data.roles), { replace: true });
      } catch {
        setError('Impersonation session could not be established.');
      }
    })();
  }, [searchParams, setSessionFromLogin, applyMeProfile, navigate]);

  const loginMutation = useMutation({
    mutationFn: async (data: LoginRequest) => {
      const res = await apiClient.post<SessionResponse>('/api/v1/auth/login', data);
      return res.data;
    },
    onSuccess: async (data) => {
      setSessionFromLogin(data, email, undefined, Boolean(mfaChallenge));
      setMfaChallenge(null);
      try {
        const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
        applyMeProfile(me.data);
      } catch {
        // AppShell / floor shell will hydrate /me on the next authenticated render.
      }
      const b2bOnly =
        data.roles.length > 0 && data.roles.every((role) => role === 'B2B_CUSTOMER');
      const pickerOnly =
        data.roles.length > 0 && data.roles.every((role) => role === 'PICKER');
      navigate(b2bOnly ? '/showroom/catalog' : pickerOnly ? '/fulfillment' : '/dashboard');
    },
    onError: (err: AxiosError<MfaChallengeBody & { detail?: string; ssoAuthorizationUrl?: string }>) => {
      const body = err.response?.data;
      if (body?.title === 'SSO_REQUIRED' && body.ssoAuthorizationUrl) {
        window.location.href = resolveSsoHref(body.ssoAuthorizationUrl, import.meta.env.VITE_API_URL ?? '');
        return;
      }
      if (isMfaRequiredTitle(body?.title)) {
        setError('');
        setMfaChallenge(body ?? { title: 'MFA_REQUIRED_FOR_EXTERNAL_ACCESS' });
        return;
      }
      if (body?.title === 'ACCESS_DENIED') {
        setError(body.detail || 'Access denied from this network.');
        return;
      }
      setError('Invalid email or password. Try owner@demo.test / password123');
    },
  });

  const magicConsumeMutation = useMutation({
    mutationFn: async (token: string) => {
      const res = await apiClient.post<SessionResponse>('/api/v1/auth/magic-login/consume', { token });
      return res.data;
    },
    onSuccess: async (data) => {
      setSessionFromLogin(data, email);
      try {
        const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
        applyMeProfile(me.data);
        navigate(postLoginPath(me.data.roles), { replace: true });
        return;
      } catch {
        // Floor shell hydrates /me after navigation.
      }
      navigate(postLoginPath(data.roles), { replace: true });
    },
    onError: async () => {
      try {
        const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
        applyMeProfile(me.data);
        navigate(postLoginPath(me.data.roles), { replace: true });
      } catch {
        setError('Magic link expired or already used.');
      }
    },
  });

  const magicRequestMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<{ status: string }>(
        '/api/v1/auth/magic-login',
        { email },
      );
      return res.data;
    },
    onSuccess: () => {
      setMagicSent(true);
      setError('');
    },
    onError: () => setError('Could not send a magic link for that email.'),
  });

  const discoveryMutation = useMutation({
    mutationFn: async (forEmail: string) => {
      const res = await apiClient.get<HrdResponse>('/api/v1/auth/discovery', {
        params: { email: forEmail },
      });
      return res.data;
    },
    onSuccess: (data) => {
      const next = nextHrdStep(data);
      setRealm(data);
      if (next === 'sso-redirect' && data.ssoUrl) {
        window.location.href = resolveSsoHref(data.ssoUrl, import.meta.env.VITE_API_URL ?? '');
        return;
      }
      setStep(next);
    },
    onError: () => {
      setRealm(null);
      setStep('password');
    },
  });

  useEffect(() => {
    const token = searchParams.get('magic');
    if (!claimMagicLinkToken(token)) return;
    const next = new URLSearchParams(searchParams);
    next.delete('magic');
    const qs = next.toString();
    navigate({ pathname: '/login', search: qs ? `?${qs}` : '' }, { replace: true });
    magicConsumeMutation.mutate(token as string);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startSso = (authorizationUrl?: string | null) => {
    if (!authorizationUrl) return;
    window.location.href = resolveSsoHref(authorizationUrl, import.meta.env.VITE_API_URL ?? '');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (step === 'email') {
      discoveryMutation.mutate(email);
      return;
    }
    loginMutation.mutate({ email, password, targetApp: 'WMS' });
  };

  const runMfaChallenge = async () => {
    if (!mfaChallenge) return;
    setMfaBusy(true);
    setError('');
    try {
      const stored = readTerminalPasskey();
      const assertion = await completeMfaAssertion(mfaChallenge, stored);
      loginMutation.mutate({
        email,
        password,
        targetApp: 'WMS',
        mfaCredentialId: assertion.mfaCredentialId,
        mfaChallenge: assertion.mfaChallenge,
        mfaSignature: assertion.mfaSignature,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Passkey required for off-network access');
    } finally {
      setMfaBusy(false);
    }
  };

  const showPassword = !mfaChallenge && (step === 'password' || step === 'sso-optional');
  const showSso = !mfaChallenge && step === 'sso-optional' && Boolean(realm?.ssoUrl);

  return (
    <div className="flex min-h-screen">
      <div className="relative hidden flex-1 flex-col justify-between overflow-hidden bg-[#55ACEE] p-12 lg:flex">
        <div className="flex items-center gap-4">
          <BrandLogo inverted />
        </div>

        <div className="max-w-lg">
          <h1 className="text-4xl font-bold leading-tight text-white [text-wrap:balance] xl:text-[2.75rem]">
            {t('login.headline')}
          </h1>
          <p className="mt-5 text-lg leading-relaxed text-white/95">{t('login.subhead')}</p>
        </div>

        <p className="text-sm text-white/75">{t('login.copyright', { year: new Date().getFullYear() })}</p>
      </div>

      <div className="relative flex flex-1 items-center justify-center bg-[#1a2332] p-6">
        <Sparkles
          className="pointer-events-none absolute bottom-8 right-8 h-8 w-8 text-white/20"
          aria-hidden="true"
        />

        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center justify-center gap-3 lg:hidden">
            <BrandLogo inverted />
          </div>

          <div className="rounded-xl border border-white/10 bg-[#243044] p-8 shadow-[0_12px_40px_rgba(0,0,0,0.45)]">
            <div className="mb-8 text-center">
              <h2 className="text-2xl font-bold text-white">{t('login.welcome')}</h2>
              {realm?.companyName ? (
                <p className="mt-2 text-sm text-white/60" data-testid="login-company">
                  {realm.companyName}
                </p>
              ) : null}
            </div>

            <form onSubmit={handleSubmit} className="space-y-4" data-testid={`login-step-${step}`}>
              <Input
                label={t('login.email')}
                tone="inverse"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                required
                autoComplete="email"
                readOnly={step !== 'email'}
                data-testid="login-email"
                className="border-white/15 bg-[#1a2332] text-white placeholder:text-white/40 focus:border-[#55ACEE] focus:ring-[#55ACEE]/30"
              />

              {showSso ? (
                <Button
                  type="button"
                  className="w-full border-0 bg-[#55ACEE] text-white shadow-[0_0_24px_rgba(85,172,238,0.45)] hover:bg-[#4a9de0]"
                  size="lg"
                  data-testid="login-sso-primary"
                  onClick={() => startSso(realm?.ssoUrl)}
                >
                  {t('login.useSso', { company: realm?.companyName || 'SSO' })}
                </Button>
              ) : null}

              {showPassword ? (
                <Input
                  label={t('login.password')}
                  tone="inverse"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  autoComplete="current-password"
                  data-testid="login-password"
                  className="border-white/15 bg-[#1a2332] text-white placeholder:text-white/40 focus:border-[#55ACEE] focus:ring-[#55ACEE]/30"
                />
              ) : null}

              {mfaChallenge ? (
                <div className="space-y-3" data-testid="login-mfa-challenge">
                  <p className="text-sm text-white/80">
                    This role requires a passkey when you are off the corporate network.
                  </p>
                  <Button
                    type="button"
                    className="w-full border-0 bg-[#55ACEE] text-white shadow-[0_0_24px_rgba(85,172,238,0.45)] hover:bg-[#4a9de0]"
                    size="lg"
                    data-testid="login-mfa-submit"
                    loading={mfaBusy || loginMutation.isPending}
                    onClick={() => void runMfaChallenge()}
                  >
                    Continue with passkey
                  </Button>
                </div>
              ) : null}

              {error && (
                <p
                  role="alert"
                  data-testid="login-error"
                  className="rounded-lg border border-red-400/40 bg-red-500/10 px-3 py-2 text-sm text-red-300"
                >
                  {error}
                </p>
              )}
              {magicSent && !error && (
                <p className="rounded-lg border border-[#55ACEE]/40 bg-[#55ACEE]/10 px-3 py-2 text-sm text-[#7ec8f7]">
                  Magic link sent — open Mailpit at localhost:8025 and use the Sign in button. The link works once.
                </p>
              )}

              {step === 'email' ? (
                <Button
                  type="submit"
                  className="w-full border-0 bg-[#55ACEE] text-white shadow-[0_0_24px_rgba(85,172,238,0.45)] hover:bg-[#4a9de0]"
                  size="lg"
                  data-testid="login-continue"
                  loading={discoveryMutation.isPending}
                >
                  {t('login.continue')}
                </Button>
              ) : mfaChallenge ? null : (
                <>
                  <Button
                    type="submit"
                    className="w-full border-0 bg-[#55ACEE] text-white shadow-[0_0_24px_rgba(85,172,238,0.45)] hover:bg-[#4a9de0]"
                    size="lg"
                    data-testid="login-submit"
                    loading={loginMutation.isPending}
                  >
                    {t('common.signIn')}
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    className="w-full border-white/15 bg-transparent text-white hover:bg-white/5"
                    data-testid="login-magic-link"
                    loading={magicRequestMutation.isPending || magicConsumeMutation.isPending}
                    onClick={() => {
                      setError('');
                      setMagicSent(false);
                      magicRequestMutation.mutate();
                    }}
                  >
                    {t('login.magicLink')}
                  </Button>
                  <button
                    type="button"
                    className="w-full text-center text-sm text-white/50 hover:text-white"
                    data-testid="login-change-email"
                    onClick={() => {
                      setStep('email');
                      setRealm(null);
                      setPassword('');
                      setError('');
                      setMfaChallenge(null);
                    }}
                  >
                    {t('login.changeEmail')}
                  </button>
                </>
              )}
            </form>

            <p className="mt-6 text-center text-sm">
              <Link
                to="/signup"
                className="font-medium text-[#55ACEE] hover:text-[#7ec8f7] hover:underline"
              >
                Create your company
              </Link>
            </p>
          </div>

          <p className="mt-6 text-center text-xs text-white/40">
            Demo: owner@demo.test · password123 (also @acme / @northwind / @pacific)
          </p>
        </div>
      </div>
    </div>
  );
}
