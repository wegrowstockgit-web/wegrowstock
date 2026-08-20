import { useEffect, useState } from 'react';

type FeatureFlagsResponse = {
  flags?: string[];
};

async function fetchEnabledFeatureFlags(): Promise<string[]> {
  const response = await fetch('/api/v1/feature-flags', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const body = (await response.json()) as FeatureFlagsResponse;
  return Array.isArray(body.flags) ? body.flags.map(String) : [];
}

/**
 * Lightweight bootstrap hook for the signed-in tenant's progressive-delivery flags.
 * POS does not ship React Query; this mirrors the WMS query shape.
 */
export function useFeatureFlag(flagKey?: string) {
  const [flags, setFlags] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isError, setIsError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setIsError(false);
    fetchEnabledFeatureFlags()
      .then((next) => {
        if (!cancelled) {
          setFlags(next);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setIsError(true);
          setFlags([]);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const hasFlag = (key: string) => flags.includes(key);
  return {
    flags,
    isEnabled: flagKey ? hasFlag(flagKey) : false,
    hasFlag,
    isLoading,
    isError,
  };
}
