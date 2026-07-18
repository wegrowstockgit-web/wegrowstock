import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useSessionStore } from '@/stores/session';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { clearQueryCache, queryClient } from '@/offline/queryPersistence';

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
    !url.includes('/api/v1/auth/warehouse/login')
  );
}

function captureRequestId(headers: Record<string, unknown> | undefined) {
  const requestId = headers?.['x-request-id'];
  if (typeof requestId === 'string' && requestId) {
    useSessionStore.getState().setLastRequestId(requestId);
  }
}

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const warehouseId = useActiveWarehouseStore.getState().warehouseId;
  if (warehouseId) {
    config.headers['X-Warehouse-Id'] = warehouseId;
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
    captureRequestId(error.response?.headers as Record<string, unknown> | undefined);
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
