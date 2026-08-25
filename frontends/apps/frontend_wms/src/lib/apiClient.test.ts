import { AxiosError, AxiosHeaders } from 'axios';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const toastError = vi.fn();
vi.mock('@/components/ui/Toast', () => ({
  toast: { error: (...args: unknown[]) => toastError(...args) },
}));

import { extractApiError, notifyApiProblem, resetApiProblemToastForTests } from './apiClient';

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

describe('notifyApiProblem', () => {
  beforeEach(() => {
    toastError.mockReset();
    resetApiProblemToastForTests();
  });

  it('toasts RFC 7807 detail on 400, 409, and 422', () => {
    notifyApiProblem(axiosError({ detail: 'Quantity exceeds available ATP' }, 400));
    notifyApiProblem(axiosError({ detail: 'Bin capacity exceeded' }, 409));
    notifyApiProblem(axiosError({ detail: 'Lot number is required' }, 422));
    expect(toastError).toHaveBeenCalledTimes(3);
    expect(toastError).toHaveBeenNthCalledWith(1, 'Quantity exceeds available ATP');
    expect(toastError).toHaveBeenNthCalledWith(2, 'Bin capacity exceeded');
    expect(toastError).toHaveBeenNthCalledWith(3, 'Lot number is required');
  });

  it('does not toast 401 or a missing detail', () => {
    notifyApiProblem(axiosError({ detail: 'Unauthorized' }, 401));
    notifyApiProblem(axiosError({ title: 'CONFLICT' }, 409));
    expect(toastError).not.toHaveBeenCalled();
  });

  it('dedupes the same problem so local onError toasts do not double-fire', () => {
    const err = axiosError({ detail: 'Wave already released' }, 409);
    notifyApiProblem(err);
    notifyApiProblem(err);
    expect(toastError).toHaveBeenCalledTimes(1);
  });

  it('skips when the request opted out', () => {
    const err = axiosError({ detail: 'Inline only' }, 422);
    err.config = { headers: new AxiosHeaders(), skipErrorToast: true };
    notifyApiProblem(err);
    expect(toastError).not.toHaveBeenCalled();
  });
});
