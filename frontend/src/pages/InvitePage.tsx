import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { UserPlus } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardHeader } from '@/components/ui/Card';

export function InvitePage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const acceptMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/invitations/accept', {
        token,
        password,
        displayName,
      });
    },
    onSuccess: () => {
      navigate('/login', { state: { message: 'Account created. Sign in with your company slug and email.' } });
    },
    onError: () => {
      setError('This invitation is invalid or has expired.');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;
    setError('');
    acceptMutation.mutate();
  };

  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <Card className="max-w-md p-8 text-center">
          <p className="text-danger">Invalid invitation link.</p>
          <Link to="/login" className="mt-4 inline-block text-accent hover:underline">
            Go to login
          </Link>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface p-6">
      <Card className="w-full max-w-md p-8">
        <CardHeader title="Accept invitation" description="Set up your account to join the team" />

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <Input
            label="Your name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
          />
          <Input
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
          {error && <p className="text-sm text-danger">{error}</p>}
          <Button type="submit" className="w-full" size="lg" loading={acceptMutation.isPending}>
            <UserPlus className="h-4 w-4" />
            Join team
          </Button>
        </form>
      </Card>
    </div>
  );
}
