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
});

let refreshPromise: Promise<string | null> | null = null;

async function handleAuthFailure(): Promise<void> {
  useSessionStore.getState().clearSession();
  queryClient.clear();
  await clearQueryCache();
}

async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken, updateTokens } = useSessionStore.getState();
  if (!refreshToken) {
    await handleAuthFailure();
    return null;
  }

  try {
    const path = '/api/v1/auth/refresh';
    const response = await axios.post<{ accessToken: string; refreshToken?: string }>(
      API_BASE_URL ? `${API_BASE_URL}${path}` : path,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } }
    );
    const { accessToken, refreshToken: newRefresh } = response.data;
    updateTokens(accessToken, newRefresh);
    return accessToken;
  } catch {
    await handleAuthFailure();
    return null;
  }
}

function singleFlightRefresh(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function accessTokenExpiresSoon(token: string, skewMs = 60_000): boolean {
  try {
    const [, payload] = token.split('.');
    if (!payload) return true;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as {
      exp?: number;
    };
    return typeof json.exp !== 'number' || json.exp * 1000 <= Date.now() + skewMs;
  } catch {
    return true;
  }
}

/**
 * Ensure a valid access token before offline queue flush. Refreshes only when
 * the access token is missing or near expiry so mid-session rotation does not
 * invalidate other clients holding the prior refresh token.
 */
export async function ensureFreshSession(): Promise<boolean> {
  const { accessToken, refreshToken } = useSessionStore.getState();
  if (!refreshToken && !accessToken) {
    return false;
  }
  if (accessToken && !accessTokenExpiresSoon(accessToken)) {
    return true;
  }
  if (!refreshToken) {
    return !!accessToken;
  }
  const token = await singleFlightRefresh();
  return !!token;
}

function isAuthFailure(status?: number): boolean {
  // Only 401 means the session is invalid. 403 is an RBAC denial and must not
  // trigger refresh/logout (otherwise floor roles get signed out on office APIs).
  return status === 401;
}

function isProtectedApiRequest(url?: string): boolean {
  if (!url) return false;
  return url.includes('/api/v1/') && !url.includes('/api/v1/auth/login') && !url.includes('/api/v1/auth/signup');
}

function captureRequestId(headers: Record<string, unknown> | undefined) {
  const requestId = headers?.['x-request-id'];
  if (typeof requestId === 'string' && requestId) {
    useSessionStore.getState().setLastRequestId(requestId);
  }
}

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useSessionStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
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
      const newToken = await singleFlightRefresh();
      if (newToken) {
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      }
      await handleAuthFailure();
    }

    return Promise.reject(error);
  }
);
