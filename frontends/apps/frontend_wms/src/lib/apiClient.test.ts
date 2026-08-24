import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { extractApiError } from './apiClient';

function axiosError(data: unknown, status = 409): AxiosError {
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: 'Conflict',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data,
  });
}

describe('extractApiError', () => {
  it('returns RFC 7807 detail from an Axios error', () => {
    expect(
      extractApiError(
        axiosError({ detail: 'An open invitation already exists for this email' }),
        'Failed to send invitation',
      ),
    ).toBe('An open invitation already exists for this email');
  });

  it('returns the fallback when detail is missing', () => {
    expect(extractApiError(axiosError({ title: 'CONFLICT' }), 'Failed to send invitation')).toBe(
      'Failed to send invitation',
    );
  });

  it('returns the fallback for non-Axios errors', () => {
    expect(extractApiError(new Error('boom'), 'Could not create customer.')).toBe(
      'Could not create customer.',
    );
  });
});
