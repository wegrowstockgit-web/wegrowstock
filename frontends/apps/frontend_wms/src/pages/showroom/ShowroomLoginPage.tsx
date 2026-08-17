import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Mail, Package } from 'lucide-react';
import { apiClient } from '@/api/client';
import { requestShowroomMagicLink } from '@/api/portal';
import type { SessionResponse } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useSessionStore } from '@/stores/session';
import { claimMagicLinkToken } from '@/lib/magicLinkConsume';

interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  grantedPermissions?: string[];
  enabledModules?: string[];
}

export function ShowroomLoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setSessionFromLogin = useSessionStore((s) => s.setSessionFromLogin);
  const applyMeProfile = useSessionStore((s) => s.applyMeProfile);
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);

  const consumeMutation = useMutation({
    mutationFn: async (token: string) => {
      const res = await apiClient.post<SessionResponse>('/api/v1/auth/magic-login/consume', { token });
      return res.data;
    },
    onSuccess: async (data) => {
      setSessionFromLogin(data, email);
      try {
        const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
        applyMeProfile(me.data);
      } catch {
        // Catalog hydrates /me after navigation.
      }
      navigate('/showroom/catalog', { replace: true });
    },
    onError: async () => {
      try {
        const me = await apiClient.get<MeResponse>('/api/v1/auth/me');
        applyMeProfile(me.data);
        navigate('/showroom/catalog', { replace: true });
      } catch {
        setError('Magic link expired or already used.');
      }
    },
  });

  const requestMutation = useMutation({
    mutationFn: () => requestShowroomMagicLink(email),
    onSuccess: () => {
      setSent(true);
      setError('');
    },
    onError: () => setError('Could not send a login link for that email.'),
  });

  useEffect(() => {
    const token = searchParams.get('magic');
    if (!claimMagicLinkToken(token)) return;
    const next = new URLSearchParams(searchParams);
    next.delete('magic');
    const qs = next.toString();
    navigate({ pathname: '/showroom/login', search: qs ? `?${qs}` : '' }, { replace: true });
    consumeMutation.mutate(token as string);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (sent) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface px-4" data-testid="showroom-login-sent">
        <Card className="w-full max-w-md p-8 text-center">
          <Mail className="mx-auto h-12 w-12 text-accent" />
          <h1 className="mt-4 text-2xl font-bold text-text">Check your email for your login link</h1>
          <p className="mt-2 text-sm text-text-muted">
            We sent a one-click sign-in link to {email}. It expires in 15 minutes.
          </p>
          <Link
            to="/showroom/catalog"
            className="mt-6 inline-flex h-10 items-center rounded-md border border-border bg-surface-raised px-4 text-sm font-medium text-text hover:bg-surface-overlay"
          >
            Continue browsing
          </Link>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface px-4">
      <Card className="w-full max-w-md p-8">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent text-text-inverse">
            <Package className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-text">Wholesale sign in</h1>
            <p className="text-sm text-text-muted">Passwordless — we email you a secure link</p>
          </div>
        </div>

        <form
          className="space-y-4"
          data-testid="showroom-login-form"
          onSubmit={(e) => {
            e.preventDefault();
            setError('');
            requestMutation.mutate();
          }}
        >
          <Input
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            autoFocus
          />
          {error && <p className="text-sm text-danger">{error}</p>}
          <Button
            type="submit"
            className="w-full"
            size="lg"
            loading={requestMutation.isPending || consumeMutation.isPending}
          >
            Email me a login link
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-text-muted">
          New buyer?{' '}
          <Link to="/showroom/apply" className="font-medium text-accent hover:underline">
            Apply for a wholesale account
          </Link>
        </p>
      </Card>
    </div>
  );
}
