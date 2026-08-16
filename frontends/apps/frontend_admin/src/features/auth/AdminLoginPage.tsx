import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { type AxiosError } from 'axios';
import { Shield } from 'lucide-react';
import { Button, Input } from '@invsys/shared-ui';
import { adminLogin } from '@/features/tenants/api';
import { useAdminSession } from '@/features/auth/adminSession';

export function AdminLoginPage() {
  const navigate = useNavigate();
  const setSession = useAdminSession((s) => s.setSession);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const loginMutation = useMutation({
    mutationFn: () => adminLogin(email, password),
    onSuccess: (data) => {
      setSession(data.email);
      navigate('/', { replace: true });
    },
    onError: (err: AxiosError) => {
      setError(
        err.response?.status === 401
          ? 'Invalid email or password.'
          : 'Unable to sign in. Please try again.',
      );
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    loginMutation.mutate();
  };

  return (
    <div
      className="grid min-h-screen bg-surface lg:grid-cols-[minmax(0,1.1fr)_minmax(22rem,28rem)]"
      data-testid="admin-login-page"
    >
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-surface-raised px-12 py-12 lg:flex">
        <div>
          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-accent/15 text-accent">
            <Shield className="h-5 w-5" aria-hidden />
          </div>
          <p className="mt-8 text-[11px] font-medium uppercase tracking-[0.16em] text-text-muted">
            InvSys Control Plane
          </p>
          <h1 className="mt-3 max-w-md text-3xl font-semibold tracking-tight text-text">
            Super Admin workspace
          </h1>
          <p className="mt-3 max-w-md text-sm leading-6 text-text-muted">
            Manage tenant packaging, billing, compliance, and platform operations from one console.
          </p>
        </div>
        <p className="text-xs text-text-muted">admin.invsys.com</p>
      </aside>

      <main className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent/15 text-accent">
              <Shield className="h-5 w-5" aria-hidden />
            </div>
            <div>
              <p className="text-[11px] font-medium uppercase tracking-[0.14em] text-text-muted">
                InvSys Control Plane
              </p>
              <h1 className="text-xl font-semibold tracking-tight">Super Admin sign in</h1>
            </div>
          </div>

          <div className="hidden lg:block">
            <h1 className="text-xl font-semibold tracking-tight">Super Admin sign in</h1>
            <p className="mt-1 text-sm text-text-muted">Use your platform administrator credentials.</p>
          </div>

          <form className="mt-6 space-y-4" onSubmit={handleSubmit} data-testid="admin-login-form">
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setEmail(e.target.value)}
              data-testid="admin-login-email"
            />
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setPassword(e.target.value)}
              data-testid="admin-login-password"
            />
            {error ? (
              <p className="text-sm text-danger" role="alert" data-testid="admin-login-error">
                {error}
              </p>
            ) : null}
            <Button
              type="submit"
              className="w-full"
              loading={loginMutation.isPending}
              data-testid="admin-login-submit"
            >
              Sign in
            </Button>
          </form>
        </div>
      </main>
    </div>
  );
}
