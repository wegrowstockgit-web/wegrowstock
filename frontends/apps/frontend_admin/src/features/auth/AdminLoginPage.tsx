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
    <div className="flex min-h-screen items-center justify-center bg-surface px-4">
      <div className="w-full max-w-md rounded-lg border border-border bg-surface-raised p-8 shadow-card">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent/15 text-accent">
            <Shield className="h-5 w-5" aria-hidden />
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
              InvSys Control Plane
            </p>
            <h1 className="text-xl font-semibold tracking-tight">Super Admin sign in</h1>
          </div>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit} data-testid="admin-login-form">
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
    </div>
  );
}
