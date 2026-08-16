import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { languageForUi, defaultSessionState, fetchPosSession, readCachedSession, type PosSessionState } from './posSession';
import { translate, type PosLanguage, type PosMessageKey } from './i18n';

type PosSessionContextValue = {
  session: PosSessionState;
  language: PosLanguage;
  t: (key: PosMessageKey, vars?: Record<string, string>) => string;
  refresh: () => Promise<void>;
};

const PosSessionContext = createContext<PosSessionContextValue | null>(null);

export function PosSessionProvider({
  children,
  initial,
  disableFetch = false,
}: {
  children: ReactNode;
  initial?: PosSessionState;
  disableFetch?: boolean;
}) {
  const [session, setSession] = useState<PosSessionState>(
    () => initial ?? readCachedSession() ?? defaultSessionState(),
  );

  const refresh = async () => {
    if (disableFetch) return;
    try {
      const next = await fetchPosSession();
      setSession(next);
    } catch {
      const cached = readCachedSession();
      if (cached) setSession(cached);
    }
  };

  useEffect(() => {
    if (disableFetch) return;
    void refresh();
  }, [disableFetch]);

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = languageForUi(session);
    }
  }, [session]);

  const value = useMemo<PosSessionContextValue>(() => {
    const language = languageForUi(session);
    return {
      session,
      language,
      t: (key, vars) => translate(language, key, vars),
      refresh,
    };
  }, [session]);

  return <PosSessionContext.Provider value={value}>{children}</PosSessionContext.Provider>;
}

export function usePosSession(): PosSessionContextValue {
  const ctx = useContext(PosSessionContext);
  if (ctx) return ctx;
  const session = defaultSessionState();
  const language = languageForUi(session);
  return {
    session,
    language,
    t: (key, vars) => translate(language, key, vars),
    refresh: async () => undefined,
  };
}
