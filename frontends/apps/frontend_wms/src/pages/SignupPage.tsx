import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2 } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { SessionResponse, SignupRequest } from '@/api/types';
import { useSessionStore } from '@/stores/session';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';

function slugify(name: string) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

export function SignupPage() {
  const navigate = useNavigate();
  const setSessionFromLogin = useSessionStore((s) => s.setSessionFromLogin);
  const [error, setError] = useState('');
  const [form, setForm] = useState({
    companyName: '',
    email: '',
    password: '',
    displayName: '',
  });

  const signupMutation = useMutation({
    mutationFn: async () => {
      const payload: SignupRequest = {
        companyName: form.companyName,
        slug: slugify(form.companyName),
        email: form.email,
        password: form.password,
        displayName: form.displayName,
      };
      const res = await apiClient.post<SessionResponse>('/api/v1/auth/signup', payload);
      return res.data;
    },
    onSuccess: (data) => {
      setSessionFromLogin(data, form.email, form.displayName);
      navigate('/dashboard');
    },
    onError: () => {
      setError('Signup failed. Company slug may already exist.');
    },
  });

  const canSubmit =
    form.companyName.trim().length >= 2 &&
    form.email.includes('@') &&
    form.password.length >= 8 &&
    form.displayName.trim().length >= 1;

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface p-6">
      <Card className="w-full max-w-lg p-8">
        <div className="mb-8 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent text-white">
            <Building2 className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-xl font-bold">Create your company</h1>
            <p className="text-sm text-text-secondary">Live in minutes — warehouse included</p>
          </div>
        </div>

        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            setError('');
            signupMutation.mutate();
          }}
        >
          <Input
            label="Company name"
            value={form.companyName}
            onChange={(e) => setForm({ ...form, companyName: e.target.value })}
            required
          />
          <Input
            label="Your name"
            value={form.displayName}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            required
          />
          <Input
            label="Work email"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
          <Input
            label="Password"
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
          />
          {form.companyName && (
            <p className="text-xs text-text-secondary">
              Company URL slug: <strong>{slugify(form.companyName) || '—'}</strong>
            </p>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}
          <Button type="submit" className="w-full" disabled={!canSubmit} loading={signupMutation.isPending}>
            Create account
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-text-secondary">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-accent hover:underline">
            Sign in
          </Link>
        </p>
      </Card>
    </div>
  );
}
