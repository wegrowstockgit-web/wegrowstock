import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useHardwareScanner } from './useHardwareScanner';

describe('useHardwareScanner', () => {
  it('returns isSupported false without throwing when serial and bluetooth are absent', () => {
    const onScan = vi.fn();
    const { result } = renderHook(() => useHardwareScanner({ onScan, enabled: true }));

    expect(result.current.isSupported).toBe(false);
    expect(result.current.isBluetoothSupported).toBe(false);
    expect(result.current.isSerialSupported).toBe(false);
    expect(result.current.hardwareStatus).toBe('UNSUPPORTED');
    expect(result.current.triggerCamera).toBe(false);
    expect(typeof result.current.ingestScan).toBe('function');
    expect(typeof result.current.setTriggerCamera).toBe('function');
  });
});
