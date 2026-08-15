import { describe, expect, it } from 'vitest';
import { fitWithin, DEFAULT_MAX_DIMENSION } from '@/utils/imageCompression';

describe('fitWithin', () => {
  it('leaves small images unchanged', () => {
    expect(fitWithin(800, 600, DEFAULT_MAX_DIMENSION)).toEqual({ width: 800, height: 600 });
  });

  it('scales landscape to max edge', () => {
    expect(fitWithin(2048, 1024, 1024)).toEqual({ width: 1024, height: 512 });
  });

  it('scales portrait to max edge', () => {
    expect(fitWithin(800, 2400, 1024)).toEqual({ width: 341, height: 1024 });
  });
});
