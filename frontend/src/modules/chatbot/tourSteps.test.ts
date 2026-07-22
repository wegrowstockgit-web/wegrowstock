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

  it('receiving-to-allocation is a 6-step multi-route journey', () => {
    const steps = getWorkflowTour('receiving-to-allocation');
    expect(steps).toHaveLength(6);
    expect(steps.map((s) => s.route)).toEqual([
      '/purchase-orders',
      '/purchase-orders',
      '/inbound/receive',
      '/inbound/receive',
      '/sales-orders',
      '/sales-orders',
    ]);
    expect(steps[1].transition).toEqual({
      route: '/inbound/receive',
      nextStep: 2,
      href: '/inbound/receive?po=PO-2026-00001',
    });
    expect(steps[3].transition?.route).toBe('/sales-orders');
    expect(steps[5].doneBtnText).toMatch(/Finish Onboarding/i);
    expect(steps[0].description).toMatch(/dock ticket|inbound|purchase/i);
  });
});

