import { describe, expect, it } from 'vitest';
import { isUuidv7, uuidv7 } from './uuidv7';

describe('uuidv7', () => {
  it('emits a version-7 UUID', () => {
    const id = uuidv7(1_700_000_000_000, () => 0xab);
    expect(isUuidv7(id)).toBe(true);
    expect(id.charAt(14)).toBe('7');
  });

  it('orders later timestamps after earlier ones', () => {
    const a = uuidv7(1, () => 1);
    const b = uuidv7(2, () => 1);
    expect(a < b).toBe(true);
  });

  it('rejects non-v7 strings', () => {
    expect(isUuidv7('not-a-uuid')).toBe(false);
    expect(isUuidv7('00000000-0000-4000-8000-000000000000')).toBe(false);
  });
});
