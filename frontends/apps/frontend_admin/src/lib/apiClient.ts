import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const MUTATING = new Set(['post', 'put', 'patch', 'delete']);

function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') {
    return undefined;
  }
  const parts = document.cookie.split(';');
  for (const part of parts) {
    const trimmed = part.trim();
    if (trimmed.startsWith(`${name}=`)) {
      return decodeURIComponent(trimmed.slice(name.length + 1));
    }
  }
  return undefined;
}

function applyCsrfHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const method = (config.method ?? 'get').toLowerCase();
  if (!MUTATING.has(method)) {
    return config;
  }
  const token = readCookie('XSRF-TOKEN');
  if (token) {
    config.headers['X-XSRF-TOKEN'] = token;
  }
  return config;
}

function isCsrfFailure(error: AxiosError): boolean {
  if (error.response?.status !== 403) {
    return false;
  }
  const data = error.response.data;
  const blob = typeof data === 'string' ? data : JSON.stringify(data ?? {});
  return /csrf|xsrf/i.test(blob);
}

/**
 * Control-plane Axios client.
 * Cookie session + CSRF: Spring CookieCsrfTokenRepository writes {@code XSRF-TOKEN};
 * mutating requests send it as {@code X-XSRF-TOKEN}.
 */
export const apiClient = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

apiClient.interceptors.request.use((config) => applyCsrfHeader(config));

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _csrfRetry?: boolean }) | undefined;
    if (!config || config._csrfRetry || !isCsrfFailure(error)) {
      return Promise.reject(error);
    }
    config._csrfRetry = true;
    await ensureCsrfCookie();
    applyCsrfHeader(config);
    return apiClient.request(config);
  },
);

/** Warm the CSRF cookie before the first mutating call (login is CSRF-exempt). */
export async function ensureCsrfCookie(): Promise<void> {
  await apiClient.get('/api/v1/control-plane/auth/csrf');
}
