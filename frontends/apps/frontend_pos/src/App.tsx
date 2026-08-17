import { useEffect, type ReactNode } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { RegisterPage } from '@/pages/RegisterPage';
import { LoginPage } from '@/pages/LoginPage';
import { ScannerSecurityGate } from '@/components/ScannerSecurityGate';
import { startOutboxPolling } from '@/lib/syncWorker';
import { PosSessionProvider, usePosSession } from '@/lib/PosSessionContext';

function Fallback() {
  const { t } = usePosSession();
  return (
    <div className="flex h-full items-center justify-center bg-[#e8edf4]">
      <Link className="text-lg underline" to="/">
        {t('app.back')}
      </Link>
    </div>
  );
}

function BootSplash() {
  return <div className="pos-login-shell" data-testid="pos-boot" />;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { hydrated, isAuthenticated } = usePosSession();
  if (!hydrated) return <BootSplash />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}

function RequireGuest({ children }: { children: ReactNode }) {
  const { hydrated, isAuthenticated } = usePosSession();
  if (!hydrated) return <BootSplash />;
  if (isAuthenticated) return <Navigate to="/" replace />;
  return children;
}

export function AppRoutes() {
  const { isAuthenticated } = usePosSession();
  useEffect(() => {
    if (!isAuthenticated) return;
    return startOutboxPolling();
  }, [isAuthenticated]);

  return (
    <div className="h-full">
      <Routes>
        <Route
          path="/"
          element={
            <RequireAuth>
              <ScannerSecurityGate>
                <RegisterPage />
              </ScannerSecurityGate>
            </RequireAuth>
          }
        />
        <Route path="/register" element={<Navigate to="/" replace />} />
        <Route
          path="/login"
          element={
            <RequireGuest>
              <LoginPage />
            </RequireGuest>
          }
        />
        <Route path="*" element={<Fallback />} />
      </Routes>
    </div>
  );
}

export function App() {
  return (
    <PosSessionProvider>
      <AppRoutes />
    </PosSessionProvider>
  );
}
