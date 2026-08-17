import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useSessionStore } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { clearQueryCache, queryClient } from '@/offline/queryPersistence';
import { getTrainingGuard } from '@/lib/training/active';
import { recordSupportNetworkError } from '@/lib/chatbot/active';

// Empty base URL: requests use /api/v1/... and are proxied by Vite (dev) or nginx (Docker).
export const API_BASE_URL = import.meta.env.VITE_API_URL ?? '';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
  withCredentials: true,
});

let refreshPromise: Promise<boolean> | null = null;

async function handleAuthFailure(): Promise<void> {
  useSessionStore.getState().clearSession();
  queryClient.clear();
  await clearQueryCache();
}

async function refreshAccessToken(): Promise<boolean> {
  try {
    const path = '/api/v1/auth/refresh';
    await axios.post(
      API_BASE_URL ? `${API_BASE_URL}${path}` : path,
      {},
      {
        headers: { 'Content-Type': 'application/json' },
        withCredentials: true,
      },
    );
    return true;
  } catch {
    await handleAuthFailure();
    return false;
  }
}

function singleFlightRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/**
 * Ensure a valid cookie session before offline queue flush.
 */
export async function ensureFreshSession(): Promise<boolean> {
  const { authenticated } = useSessionStore.getState();
  if (!authenticated) {
    return false;
  }
  return singleFlightRefresh();
}

function isAuthFailure(status?: number): boolean {
  return status === 401;
}

function isProtectedApiRequest(url?: string): boolean {
  if (!url) return false;
  return (
    url.includes('/api/v1/') &&
    !url.includes('/api/v1/auth/login') &&
    !url.includes('/api/v1/auth/signup') &&
    !url.includes('/api/v1/auth/warehouse/login') &&
    !url.includes('/api/v1/auth/discovery') &&
    !url.includes('/api/v1/auth/sso-discover')
  );
}

function headerString(
  headers: Record<string, unknown> | undefined,
  ...keys: string[]
): string | null {
  if (!headers) return null;
  for (const key of keys) {
    const value = headers[key] ?? headers[key.toLowerCase()];
    if (typeof value === 'string' && value.trim()) return value.trim();
    if (Array.isArray(value) && typeof value[0] === 'string' && value[0].trim()) {
      return value[0].trim();
    }
  }
  return null;
}

function captureRequestId(headers: Record<string, unknown> | undefined) {
  const requestId = headerString(headers, 'x-request-id', 'X-Request-Id');
  if (requestId) {
    useSessionStore.getState().setLastRequestId(requestId);
  }
}

function captureSupportTelemetry(
  status: number | undefined,
  headers: Record<string, unknown> | undefined,
  message: string | undefined,
) {
  if (!status || status < 400) return;
  const traceparent = headerString(headers, 'traceparent', 'Traceparent');
  const traceId =
    headerString(headers, 'x-trace-id', 'X-Trace-Id', 'x-b3-traceid')
    ?? (traceparent ? traceparent.split('-')[1] ?? null : null)
    ?? headerString(headers, 'x-request-id', 'X-Request-Id')
    ?? useSessionStore.getState().lastRequestId;
  recordSupportNetworkError({ status, message: message ?? null, traceId });
}

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const method = (config.method ?? 'get').toLowerCase();
  const url = config.url ?? '';
  const mutating = method !== 'get' && method !== 'head' && method !== 'options';
  const allowInTraining =
    url.includes('/api/v1/support/')
    || url.includes('/api/v1/auth/');
  if (mutating && !allowInTraining) {
    const training = getTrainingGuard();
    if (training.isTrainingMode()) {
      training.recordBlockedMutation(method, url);
      return Promise.reject(
        new Error('Training mode is active — live stock changes are blocked. Exit training to continue.'),
      );
    }
  }
  if (getTrainingGuard().isTrainingMode()) {
    config.headers['X-Training-Mode'] = 'true';
  }
  const warehouseId = useActiveWarehouseStore.getState().warehouseId;
  if (warehouseId) {
    config.headers['X-Warehouse-Id'] = warehouseId;
  }
  const uiLanguage = usePreferencesStore.getState().language;
  if (uiLanguage) {
    config.headers['Accept-Language'] = uiLanguage;
    config.headers['X-User-Language'] = uiLanguage;
  }
  if (!config.headers['X-Request-Id']) {
    config.headers['X-Request-Id'] =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }
  // Let the browser set multipart boundary — default application/json breaks FormData uploads.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    if (typeof config.headers.set === 'function') {
      config.headers.set('Content-Type', false as unknown as string);
    } else {
      delete (config.headers as Record<string, unknown>)['Content-Type'];
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    captureRequestId(response.headers as Record<string, unknown>);
    return response;
  },
  async (error: AxiosError) => {
    const errHeaders = error.response?.headers as Record<string, unknown> | undefined;
    captureRequestId(errHeaders);
    captureSupportTelemetry(
      error.response?.status,
      errHeaders,
      typeof error.response?.data === 'object' && error.response?.data && 'message' in (error.response.data as object)
        ? String((error.response.data as { message?: unknown }).message ?? error.message)
        : error.message,
    );
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    if (
      isAuthFailure(error.response?.status) &&
      originalRequest &&
      !originalRequest._retry &&
      isProtectedApiRequest(originalRequest.url) &&
      !originalRequest.url?.includes('/api/v1/auth/refresh')
    ) {
      originalRequest._retry = true;
      const ok = await singleFlightRefresh();
      if (ok) {
        return apiClient(originalRequest);
      }
      await handleAuthFailure();
    }

    return Promise.reject(error);
  },
);

export { userApi } from '@/api/users';
