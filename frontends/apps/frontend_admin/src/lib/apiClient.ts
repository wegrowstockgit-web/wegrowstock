import axios from 'axios';

/**
 * Control-plane Axios client.
 * Cookie session + CSRF: Spring CookieCsrfTokenRepository writes {@code XSRF-TOKEN};
 * Axios sends it as {@code X-XSRF-TOKEN} on mutating requests.
 */
export const apiClient = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

/** Warm the CSRF cookie before the first mutating call (login is CSRF-exempt). */
export async function ensureCsrfCookie(): Promise<void> {
  await apiClient.get('/api/v1/control-plane/auth/csrf');
}
