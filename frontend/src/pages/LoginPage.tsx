import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Boxes, Sparkles } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { type AxiosError } from 'axios';
import { apiClient } from '@/api/client';
import type { LoginRequest, SessionResponse } from '@/api/types';
import { useSessionStore } from '@/stores/session';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
}

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setSessionFromLogin = useSessionStore((s) => s.setSessionFromLogin);
  const applyMeProfile = useSessionStore((s) => s.applyMeProfile);
  const [email, setEmail] = useState('owner@demo.test');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [magicSent, setMagicSent] = useState(false);

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

  const loginMutation = useMutation({
    mutationFn: async (data: LoginRequest) => {
      const res = await apiClient.post<SessionResponse>('/api/v1/auth/login', data);
      return res.data;
    },
    onSuccess: (data) => {
      setSessionFromLogin(data, email);
      const b2bOnly =
        data.roles.length > 0 && data.roles.every((role) => role === 'B2B_CUSTOMER');
      const pickerOnly =
        data.roles.length > 0 && data.roles.every((role) => role === 'PICKER');
      navigate(b2bOnly ? '/showroom/catalog' : pickerOnly ? '/fulfillment' : '/dashboard');
    },
    onError: (err: AxiosError<{ title?: string; detail?: string; ssoAuthorizationUrl?: string }>) => {
      const body = err.response?.data;
      if (body?.title === 'SSO_REQUIRED' && body.ssoAuthorizationUrl) {
        const base = import.meta.env.VITE_API_URL ?? '';
        window.location.href = `${base}${body.ssoAuthorizationUrl}`;
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
    onSuccess: (data) => {
      setSessionFromLogin(data, email);
      navigate('/fulfillment');
    },
    onError: () => setError('Magic link expired or already used.'),
  });

  const magicRequestMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post<{ status: string; magicToken?: string }>(
        '/api/v1/auth/magic-login',
        { email }
      );
      return res.data;
    },
    onSuccess: (data) => {
      setMagicSent(true);
      setError('');
      if (data.magicToken) {
        magicConsumeMutation.mutate(data.magicToken);
      }
    },
    onError: () => setError('Could not send a magic link for that email.'),
  });

  useEffect(() => {
    const token = searchParams.get('magic');
    if (token) {
      magicConsumeMutation.mutate(token);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    loginMutation.mutate({ email, password });
  };

  return (
    <div className="flex min-h-screen">
      <div className="relative hidden flex-1 flex-col justify-between overflow-hidden bg-[#55ACEE] p-12 lg:flex">
        <div className="flex items-center gap-4">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/20 ring-1 ring-white/30 backdrop-blur-sm">
            <Boxes className="h-7 w-7 text-white" aria-hidden="true" />
          </div>
          <div className="leading-none">
            <span className="block text-3xl font-black tracking-tight text-[#1a1a2e]">INVENTORY</span>
            <span className="mt-1 block text-lg font-bold tracking-[0.15em] text-[#1a1a2e]/80">
              SYSTEM
            </span>
          </div>
        </div>

        <div className="max-w-lg">
          <h1 className="text-4xl font-bold leading-tight text-white [text-wrap:balance] xl:text-[2.75rem]">
            Run your warehouse and office from one place
          </h1>
          <p className="mt-5 text-lg leading-relaxed text-white/95">
            Real-time inventory, scan-first fulfillment, manufacturing, and embedded B2B payments — on
            one system.
          </p>
        </div>

        <p className="text-sm text-white/75">
          © {new Date().getFullYear()} InventorySystem. All rights reserved.
        </p>
      </div>

      <div className="relative flex flex-1 items-center justify-center bg-[#1a2332] p-6">
        <Sparkles
          className="pointer-events-none absolute bottom-8 right-8 h-8 w-8 text-white/20"
          aria-hidden="true"
        />

        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center justify-center gap-3 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[#55ACEE] text-white">
              <Boxes className="h-5 w-5" aria-hidden="true" />
            </div>
            <div className="leading-tight">
              <span className="block text-lg font-black text-white">INVENTORY</span>
              <span className="block text-xs font-semibold tracking-widest text-white/70">SYSTEM</span>
            </div>
          </div>

          <div className="rounded-xl border border-white/10 bg-[#243044] p-8 shadow-[0_12px_40px_rgba(0,0,0,0.45)]">
            <div className="mb-8 text-center">
              <h2 className="text-2xl font-bold text-white">Welcome back</h2>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <Input
                label="Email"
                tone="inverse"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                required
                autoComplete="email"
                className="border-white/15 bg-[#1a2332] text-white placeholder:text-white/40 focus:border-[#55ACEE] focus:ring-[#55ACEE]/30"
              />
              <Input
                label="Password"
                tone="inverse"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                autoComplete="current-password"
                className="border-white/15 bg-[#1a2332] text-white placeholder:text-white/40 focus:border-[#55ACEE] focus:ring-[#55ACEE]/30"
              />

              {error && (
                <p
                  role="alert"
                  className="rounded-lg border border-red-400/40 bg-red-500/10 px-3 py-2 text-sm text-red-300"
                >
                  {error}
                </p>
              )}
              {magicSent && !error && (
                <p className="rounded-lg border border-[#55ACEE]/40 bg-[#55ACEE]/10 px-3 py-2 text-sm text-[#7ec8f7]">
                  Magic link sent — check console/email, or it will sign in automatically in demo mode.
                </p>
              )}

              <Button
                type="submit"
                className="w-full border-0 bg-[#55ACEE] text-white shadow-[0_0_24px_rgba(85,172,238,0.45)] hover:bg-[#4a9de0] hover:shadow-[0_0_32px_rgba(85,172,238,0.55)]"
                size="lg"
                loading={loginMutation.isPending}
              >
                Sign in
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="w-full border-white/15 bg-transparent text-white hover:bg-white/5"
                loading={magicRequestMutation.isPending || magicConsumeMutation.isPending}
                onClick={() => {
                  setError('');
                  setMagicSent(false);
                  magicRequestMutation.mutate();
                }}
              >
                Email magic link
              </Button>
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
