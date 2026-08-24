import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { restockLinesMissingBin, rmaActionErrorMessage } from './rmaActionError';

function axiosError(data: unknown, status = 422): AxiosError {
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: 'Unprocessable Entity',
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
    data,
  });
}

describe('rmaActionErrorMessage', () => {
  it('uses Problem Details detail when present', () => {
    expect(
      rmaActionErrorMessage(
        axiosError({ detail: 'Choose a restock target bin for every RESTOCK line' }),
      ),
    ).toBe('Choose a restock target bin for every RESTOCK line');
  });

  it('falls back when detail is missing', () => {
    expect(rmaActionErrorMessage(axiosError({ title: 'NO_DEFECT_LINES' }))).toBe(
      'An unexpected error occurred while processing the RMA.',
    );
    expect(rmaActionErrorMessage(new Error('boom'))).toBe(
      'An unexpected error occurred while processing the RMA.',
    );
  });
});

describe('restockLinesMissingBin', () => {
  it('flags RESTOCK lines without a target bin', () => {
    expect(
      restockLinesMissingBin([
        { disposition: 'RESTOCK', restockLocationId: null },
        { disposition: 'SCRAP', restockLocationId: null },
      ]),
    ).toBe(true);
    expect(
      restockLinesMissingBin([{ disposition: 'RESTOCK', restockLocationId: 'bin-1' }]),
    ).toBe(false);
  });
});
