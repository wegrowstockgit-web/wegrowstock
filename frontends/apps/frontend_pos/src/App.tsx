import { useEffect } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { RegisterPage } from '@/pages/RegisterPage';
import { LoginPage } from '@/pages/LoginPage';
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

function AppRoutes() {
  useEffect(() => startOutboxPolling(), []);

  return (
    <div className="h-full">
      <Routes>
        <Route path="/" element={<RegisterPage />} />
        <Route path="/register" element={<Navigate to="/" replace />} />
        <Route path="/login" element={<LoginPage />} />
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
