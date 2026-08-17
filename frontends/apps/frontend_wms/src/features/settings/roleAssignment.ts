export const ASSIGNABLE_ROLES = [
  'ADMIN',
  'WAREHOUSE_MANAGER',
  'PICKER',
  'VIEWER',
  'RETAIL_CASHIER',
  'RETAIL_MANAGER',
  'B2B_CUSTOMER',
  'SUPPLIER',
] as const;

export type AssignableRole = (typeof ASSIGNABLE_ROLES)[number];

export function formatRoleLabel(code: string): string {
  return code.replaceAll('_', ' ');
}

export function toggleRole(selected: string[], role: string, checked: boolean): string[] {
  if (checked) {
    return selected.includes(role) ? selected : [...selected, role];
  }
  return selected.filter((code) => code !== role);
}

export function requireAtLeastOneRole(roleIds: string[]): string | null {
  return roleIds.length === 0 ? 'Select at least one role' : null;
}
