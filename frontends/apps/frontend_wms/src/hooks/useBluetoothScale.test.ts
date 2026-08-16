import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useBluetoothScale } from './useBluetoothScale';

describe('useBluetoothScale', () => {
  it('returns isSupported false and does not throw when Web Bluetooth is missing', async () => {
    const { result } = renderHook(() => useBluetoothScale());

    expect(result.current.isBluetoothSupported).toBe(false);
    expect(result.current.isSupported).toBe(false);
    expect(result.current.supported).toBe(false);

    await act(async () => {
      await result.current.connect();
    });

    expect(result.current.error).toMatch(/not available/i);
    expect(result.current.connected).toBe(false);
  });
});
