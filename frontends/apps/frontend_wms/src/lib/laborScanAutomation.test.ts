import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  applyLaborFromScan,
  inferLaborActivityFromScan,
  resetLaborScanAutomationForTests,
} from './laborScanAutomation';
import { apiClient } from '@/api/client';
import { resetUiActionTrackerForTests, useUiActionTrackerStore } from '@/stores/uiActionTrackerStore';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const get = vi.mocked(apiClient.get);
const post = vi.mocked(apiClient.post);

describe('inferLaborActivityFromScan', () => {
  it('maps tote, LPN, and fulfillment context to PICKING', () => {
    expect(inferLaborActivityFromScan('LPN-ABC123')).toBe('PICKING');
    expect(inferLaborActivityFromScan('TOTE-A')).toBe('PICKING');
    expect(inferLaborActivityFromScan('WAVE-9')).toBe('PICKING');
    expect(inferLaborActivityFromScan('SKU-1', { pathname: '/fulfillment' })).toBe('PICKING');
    expect(inferLaborActivityFromScan('SKU-1', { mode: 'pick' })).toBe('PICKING');
  });

  it('maps PO, dock, inbound, and receive mode to RECEIVING', () => {
    expect(inferLaborActivityFromScan('PO-2026-00001')).toBe('RECEIVING');
    expect(inferLaborActivityFromScan('DOCK-1')).toBe('RECEIVING');
    expect(inferLaborActivityFromScan('ASN-44')).toBe('RECEIVING');
    expect(inferLaborActivityFromScan('SKU-1', { pathname: '/inbound/receive' })).toBe('RECEIVING');
    expect(inferLaborActivityFromScan('SKU-1', { mode: 'receive' })).toBe('RECEIVING');
  });

  it('returns null for unclassified scans', () => {
    expect(inferLaborActivityFromScan('')).toBeNull();
    expect(inferLaborActivityFromScan('WIDGET-S')).toBeNull();
  });
});

describe('applyLaborFromScan', () => {
  beforeEach(() => {
    resetLaborScanAutomationForTests();
    resetUiActionTrackerForTests();
    get.mockReset();
    post.mockReset();
  });

  it('clocks in then switches to RECEIVING for a purchase order scan', async () => {
    get.mockResolvedValue({ data: { active: false, currentActivity: null } });
    post.mockResolvedValue({ data: {} });

    await applyLaborFromScan({ barcode: 'PO-2026-1', warehouseId: 'wh-1' });

    expect(post).toHaveBeenCalledWith('/api/v1/labor/clock-in', { warehouseId: 'wh-1' });
    expect(post).toHaveBeenCalledWith('/api/v1/labor/switch-activity', { activityType: 'RECEIVING' });
    expect(useUiActionTrackerStore.getState().actions[0]?.actionType).toBe('LABOR_AUTO_SWITCH');
  });

  it('switches an active shift to PICKING for an LPN scan', async () => {
    get.mockResolvedValue({ data: { active: true, currentActivity: 'RECEIVING' } });
    post.mockResolvedValue({ data: {} });

    await applyLaborFromScan({ barcode: 'LPN-PALLET-1' });

    expect(post).not.toHaveBeenCalledWith('/api/v1/labor/clock-in', expect.anything());
    expect(post).toHaveBeenCalledWith('/api/v1/labor/switch-activity', { activityType: 'PICKING' });
  });

  it('does not switch when already on the inferred activity', async () => {
    get.mockResolvedValue({ data: { active: true, currentActivity: 'PICKING' } });

    await applyLaborFromScan({ barcode: 'TOTE-A' });

    expect(post).not.toHaveBeenCalled();
  });
});
