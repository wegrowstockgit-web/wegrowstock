import { ASSIGNABLE_ROLES, formatRoleLabel, toggleRole } from '@/features/settings/roleAssignment';

const ROLE_HINTS: Record<string, string> = {
  ADMIN: 'Full warehouse administration except ownership transfer',
  WAREHOUSE_MANAGER: 'Floor leadership, adjustments, and cycle counts',
  PICKER: 'Pick, pack, and put-away',
  VIEWER: 'Read-only operations',
  RETAIL_CASHIER: 'Retail POS register',
  RETAIL_MANAGER: 'POS supervision and voids',
  B2B_CUSTOMER: 'Customer portal access',
  SUPPLIER: 'Vendor portal access',
  OWNER: 'Tenant owner — cannot be assigned from this list',
};

export function RoleMultiSelect({
  value,
  onChange,
  exclude = [],
  includeCodes = [],
  testId = 'role-multiselect',
}: {
  value: string[];
  onChange: (next: string[]) => void;
  exclude?: string[];
  includeCodes?: string[];
  testId?: string;
}) {
  const codes = Array.from(new Set([...ASSIGNABLE_ROLES, ...includeCodes])).filter(
    (code) => !exclude.includes(code),
  );

  return (
    <fieldset data-testid={testId}>
      <legend className="mb-2 text-sm font-medium text-text">Roles</legend>
      <p className="mb-2 text-xs text-text-muted">Assign every role this person should hold. Permissions stack.</p>
      <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
        {codes.map((code) => {
          const checked = value.includes(code);
          const locked = code === 'OWNER';
          return (
            <label
              key={code}
              data-testid={`role-option-${code}`}
              className="flex items-start gap-2 rounded-md border border-border bg-surface px-3 py-2 text-sm text-text"
            >
              <input
                type="checkbox"
                className="mt-0.5"
                checked={checked}
                disabled={locked}
                aria-label={code}
                onChange={(e) => onChange(toggleRole(value, code, e.target.checked))}
              />
              <span>
                <span className="font-medium">{formatRoleLabel(code)}</span>
                {ROLE_HINTS[code] && (
                  <span className="mt-0.5 block text-xs text-text-muted">{ROLE_HINTS[code]}</span>
                )}
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
