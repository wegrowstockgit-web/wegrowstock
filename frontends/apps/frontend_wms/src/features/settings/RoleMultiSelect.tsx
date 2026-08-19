import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { roleApi } from '@/api/roles';
import type { RoleDefinition } from '@/api/types';
import { formatRoleLabel, toggleRole } from '@/features/settings/roleAssignment';

const CUSTOM_ROLE_FALLBACK = 'Custom organizational role';

function roleToken(role: RoleDefinition, selected: string[]): string {
  return selected.includes(role.id) ? role.id : role.name;
}

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
  const rolesQuery = useQuery({
    queryKey: ['roles'],
    queryFn: roleApi.list,
    retry: false,
  });

  const roles = useMemo(() => {
    const list = rolesQuery.data ?? [];
    return list.filter((role) => {
      if (exclude.includes(role.name) || exclude.includes(role.id)) {
        return false;
      }
      if (role.name === 'OWNER' && !includeCodes.includes('OWNER')) {
        return false;
      }
      return true;
    });
  }, [exclude, includeCodes, rolesQuery.data]);

  return (
    <fieldset data-testid={testId}>
      <legend className="mb-2 text-sm font-medium text-text">Roles</legend>
      <p className="mb-2 text-xs text-text-muted">Assign every role this person should hold. Permissions stack.</p>
      {rolesQuery.isLoading ? (
        <p className="text-sm text-text-muted">Loading roles…</p>
      ) : rolesQuery.isError ? (
        <p className="text-sm text-danger">Could not load roles.</p>
      ) : (
        <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
          {roles.map((role) => {
            const token = roleToken(role, value);
            const checked = value.includes(role.id) || value.includes(role.name);
            const locked = role.name === 'OWNER';
            return (
              <label
                key={role.id}
                data-testid={`role-option-${role.name}`}
                className="flex items-start gap-2 rounded-md border border-border bg-surface px-3 py-2 text-sm text-text"
              >
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={checked}
                  disabled={locked}
                  aria-label={role.name}
                  onChange={(e) => onChange(toggleRole(value, token, e.target.checked))}
                />
                <span>
                  <span className="font-medium">{formatRoleLabel(role.name)}</span>
                  <span className="mt-0.5 block text-xs text-text-muted">
                    {role.description || CUSTOM_ROLE_FALLBACK}
                  </span>
                </span>
              </label>
            );
          })}
        </div>
      )}
    </fieldset>
  );
}
