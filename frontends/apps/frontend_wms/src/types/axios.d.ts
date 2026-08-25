import type {} from 'axios';

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Skip the global RFC 7807 problem toast (inline form handling only). */
    skipErrorToast?: boolean;
  }
}
