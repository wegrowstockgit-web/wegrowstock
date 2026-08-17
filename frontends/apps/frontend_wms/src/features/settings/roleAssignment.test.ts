import { describe, expect, it } from 'vitest';
import {
  formatRoleLabel,
  requireAtLeastOneRole,
  toggleRole,
} from '@/features/settings/roleAssignment';

describe('roleAssignment', () => {
  it('formats role codes for badges', () => {
    expect(formatRoleLabel('RETAIL_MANAGER')).toBe('RETAIL MANAGER');
    expect(formatRoleLabel('PICKER')).toBe('PICKER');
  });

  it('toggles additive role ids', () => {
    expect(toggleRole(['PICKER'], 'ADMIN', true)).toEqual(['PICKER', 'ADMIN']);
    expect(toggleRole(['PICKER', 'ADMIN'], 'PICKER', false)).toEqual(['ADMIN']);
    expect(toggleRole(['PICKER'], 'PICKER', true)).toEqual(['PICKER']);
  });

  it('requires at least one role', () => {
    expect(requireAtLeastOneRole([])).toBe('Select at least one role');
    expect(requireAtLeastOneRole(['VIEWER'])).toBeNull();
  });
});
