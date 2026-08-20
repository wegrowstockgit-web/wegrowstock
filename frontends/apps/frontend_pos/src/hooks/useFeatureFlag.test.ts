import { describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useFeatureFlag } from './useFeatureFlag';

describe('useFeatureFlag', () => {
  it('loads enabled flags from the tenant bootstrap endpoint', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ flags: ['beta-dock'] }),
      }),
    );
    const { result } = renderHook(() => useFeatureFlag('beta-dock'));
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    expect(result.current.isEnabled).toBe(true);
    expect(result.current.hasFlag('missing')).toBe(false);
  });
});
