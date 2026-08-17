import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePosSession } from '@/lib/PosSessionContext';

export async function loginWithPassword(
  email: string,
  password: string,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const response = await fetchImpl('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, targetApp: 'POS' }),
  });
  if (response.status === 403) {
    throw new Error('POS access denied');
  }
  if (!response.ok) {
    throw new Error('Invalid email or password');
  }
}

export function LoginPage() {
  const navigate = useNavigate();
  const { t, refresh } = usePosSession();
  const [email, setEmail] = useState('owner@demo.test');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await loginWithPassword(email, password);
      try {
        await refresh();
      } catch {
        /* Register stays usable offline until session can be loaded. */
      }
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : t('login.failed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="pos-login-shell">
      <form
        className="pos-login-card"
        data-testid="pos-login"
        onSubmit={(event) => void onSubmit(event)}
      >
        <p className="pos-kicker">weGrowStock</p>
        <h1>{t('login.title')}</h1>
        <p className="pos-login-copy">{t('login.subtitle')}</p>
        <label>
          {t('login.email')}
          <input
            data-testid="pos-login-email"
            value={email}
            autoComplete="username"
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          {t('login.password')}
          <input
            type="password"
            data-testid="pos-login-password"
            value={password}
            autoComplete="current-password"
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        {error ? (
          <p className="pos-login-error" data-testid="pos-login-error">
            {error === 'Invalid email or password'
              ? t('login.error')
              : error === 'POS access denied'
                ? t('login.posDenied')
                : error}
          </p>
        ) : null}
        <button type="submit" disabled={busy}>
          {t('login.submit')}
        </button>
      </form>
    </div>
  );
}
