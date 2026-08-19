import { beforeEach, describe, expect, it, vi } from 'vitest';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock('@/api/client', () => ({
  apiClient: { get },
}));

import { fetchCurrentNetworkInfo } from '@/api/settings';

describe('fetchCurrentNetworkInfo', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('loads the current-ip payload', async () => {
    get.mockResolvedValue({
      data: {
        clientIp: '198.51.100.45',
        suggestedCidr: '198.51.100.45/32',
        isPrivateNetwork: false,
        networkHint: 'Public Corporate Gateway',
      },
    });
    await expect(fetchCurrentNetworkInfo()).resolves.toEqual({
      clientIp: '198.51.100.45',
      suggestedCidr: '198.51.100.45/32',
      isPrivateNetwork: false,
      networkHint: 'Public Corporate Gateway',
    });
    expect(get).toHaveBeenCalledWith('/api/v1/settings/network/current-ip');
  });
});
