import { describe, expect, it } from 'vitest';
import {
  floorTourSteps,
  getWorkflowTour,
  isFloorRoute,
  officeTourSteps,
} from './tourSteps';

describe('tourSteps', () => {
  it('marks floor routes for scanner shells', () => {
    expect(isFloorRoute('/fulfillment')).toBe(true);
    expect(isFloorRoute('/inbound/receive')).toBe(true);
    expect(isFloorRoute('/issue-supplies')).toBe(true);
    expect(isFloorRoute('/dashboard')).toBe(false);
    expect(isFloorRoute('/showroom/catalog')).toBe(false);
    expect(isFloorRoute('/settings')).toBe(false);
  });

  it('office steps cover allocation and grids', () => {
    const titles = officeTourSteps().map((s) => s.popover?.title ?? '');
    expect(titles.join(' ')).toMatch(/allocation/i);
    expect(titles.join(' ')).toMatch(/grid|columns|density/i);
  });

  it('floor steps cover scan and inbound receive', () => {
    const text = floorTourSteps()
      .map((s) => `${s.popover?.title} ${s.popover?.description}`)
      .join(' ');
    expect(text).toMatch(/scan/i);
    expect(text).toMatch(/inbound|putaway/i);
  });

  it('receiving-to-allocation is multi-route with empathy copy', () => {
    const steps = getWorkflowTour('receiving-to-allocation');
    expect(steps.map((s) => s.route)).toEqual([
      '/purchase-orders',
      '/inbound/receive',
      '/sales-orders',
      '/sales-orders',
    ]);
    expect(steps[1].description).toMatch(/unlocks inventory for the B2B portal/i);
  });
});
