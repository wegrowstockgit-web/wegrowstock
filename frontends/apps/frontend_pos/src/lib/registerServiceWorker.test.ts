import { describe, expect, it, vi } from 'vitest';
import { registerServiceWorker } from './registerServiceWorker';

describe('registerServiceWorker', () => {
  it('no-ops without a container', () => {
    expect(() => registerServiceWorker(undefined)).not.toThrow();
  });

  it('skips registration in the test runtime', () => {
    const register = vi.fn();
    registerServiceWorker({ register });
    expect(register).not.toHaveBeenCalled();
  });
});
