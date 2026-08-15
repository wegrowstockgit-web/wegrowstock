import { beforeEach, describe, expect, it } from 'vitest';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';

describe('activeWarehouse context gate', () => {
  beforeEach(() => {
    useActiveWarehouseStore.setState({
      warehouseId: null,
      warehouse: null,
      contextLocked: false,
      lockReason: null,
    });
  });

  it('rejects manual override while JWT-locked', () => {
    const wh1 = { id: 'a', name: 'Main', code: 'WH-01' };
    const wh2 = { id: 'b', name: 'Overflow', code: 'WH-02' };
    useActiveWarehouseStore.getState().lockFromJwtSingle(wh1);
    useActiveWarehouseStore.getState().setWarehouse(wh2);
    expect(useActiveWarehouseStore.getState().warehouseId).toBe('a');
    expect(useActiveWarehouseStore.getState().contextLocked).toBe(true);
  });

  it('locks from hardware SSID match', () => {
    const wh = { id: 'van-1', name: 'Truck 12', code: 'VEH-12' };
    useActiveWarehouseStore.getState().lockFromHardware(wh, 'WIFI_SSID');
    expect(useActiveWarehouseStore.getState().lockReason).toBe('HARDWARE_SSID');
    expect(useActiveWarehouseStore.getState().contextLocked).toBe(true);
  });
});
