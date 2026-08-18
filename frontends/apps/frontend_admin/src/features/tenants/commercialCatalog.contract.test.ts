import { describe, expect, it } from 'vitest';
import { APP_MODULES, MODULE_LABELS, MODULE_TIER } from '@invsys/shared-types';

/** Keep in lockstep with backend `AppModule` (13 commercially gated modules). */
const BACKEND_APP_MODULES = [
  'CORE',
  'SHOPIFY',
  'ACCOUNTING',
  'ADVANCED_FULFILLMENT',
  'MANUFACTURING',
  'DOCUMENTS',
  'MRP',
  'B2B_SHOWROOM',
  'FINTECH',
  'MESH_NETWORK',
  'RTLS_TELEMETRY',
  'AI_COPILOT',
  'RETAIL_POS',
] as const;

describe('commercial catalog (admin ↔ backend AppModule)', () => {
  it('exposes the same 13 module codes the control-plane drawer toggles', () => {
    expect(APP_MODULES).toEqual(BACKEND_APP_MODULES);
    expect(APP_MODULES).toHaveLength(13);
    expect(Object.keys(MODULE_LABELS).sort()).toEqual([...APP_MODULES].sort());
    expect(Object.keys(MODULE_TIER).sort()).toEqual([...APP_MODULES].sort());
  });
});
