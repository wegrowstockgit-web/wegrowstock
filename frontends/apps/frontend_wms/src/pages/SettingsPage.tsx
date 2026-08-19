import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Mail, Pencil, Plus, Trash2, UserPlus } from 'lucide-react';
import { apiClient } from '@/api/client';
import { userApi } from '@/api/users';
import type {
  CostCenter,
  Customer,
  InternalRequisition,
  OutboxEventItem,
  PlatformAlertItem,
  SyncLog,
  TaxRate,
  TaxScheme,
  TenantLocation,
  TenantSettingsMap,
  TenantUser,
} from '@/api/types';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { RoleMultiSelect } from '@/features/settings/RoleMultiSelect';
import { formatRoleLabel, requireAtLeastOneRole } from '@/features/settings/roleAssignment';
import { PosSettingsPanel } from '@/features/settings/PosSettingsPanel';
import { canConfigureRetailPos } from '@/features/settings/posSettingsAccess';
import { RequireModule } from '@/components/auth/RequireModule';
import { useEntitlement } from '@/hooks/useEntitlement';
import { cn } from '@/lib/utils';
import { TenantSecuritySettings } from '@/pages/TenantSecuritySettings';
import { useSessionStore } from '@/stores/session';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { ScrollFadePort } from '@/components/ui/ScrollFadePort';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { WarehouseVisualizer } from '@/features/settings/WarehouseVisualizer';
import { PartnerCatalogMappingPanel } from '@/features/settings/PartnerCatalogMappingPanel';
import { AccountingSync } from '@/features/settings/AccountingSync';
import { Integrations } from '@/features/settings/Integrations';
import { SyncConflictsPanel } from '@/features/offline/SyncConflictsPanel';
import { ActivityTimeline } from '@/features/audit/ActivityTimeline';
import { AutomationSettings } from '@/features/settings/AutomationSettings';
import { RolePermissionsMatrix } from '@/features/settings/RolePermissionsMatrix';
import { AuditLogTable } from '@/features/audit/AuditLogTable';
import { HistoricalArchivesPanel } from '@/features/audit/HistoricalArchivesPanel';
import { useToast } from '@/components/ui/Toast';
import { useTranslation } from 'react-i18next';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useClientSort } from '@/hooks/useClientSort';
import { normalizeLanguage } from '@/lib/i18n';

const TABS = [
  { id: 'profile', labelKey: 'settings.tabs.profile' },
  { id: 'users', labelKey: 'settings.tabs.users' },
  { id: 'warehouses', labelKey: 'settings.tabs.warehouses' },
  { id: 'inventory', labelKey: 'settings.tabs.inventory' },
  { id: 'documents', labelKey: 'settings.tabs.documents', requiredModule: 'DOCUMENTS' },
  { id: 'retailPos', labelKey: 'settings.tabs.retailPos', requiredModule: 'RETAIL_POS' },
  { id: 'security', labelKey: 'settings.tabs.security' },
  { id: 'reconciliation', labelKey: 'settings.tabs.reconciliation' },
  { id: 'accounting', labelKey: 'settings.tabs.accounting', requiredModule: 'ACCOUNTING' },
  { id: 'integrations', labelKey: 'settings.tabs.integrations', requiredModule: 'SHOPIFY' },
  { id: 'mesh', labelKey: 'settings.tabs.mesh', requiredModule: 'MESH_NETWORK' },
  { id: 'operations', labelKey: 'settings.tabs.operations' },
  { id: 'automations', labelKey: 'settings.tabs.automations' },
  { id: 'syncConflicts', labelKey: 'settings.tabs.syncConflicts' },
  { id: 'costCenters', labelKey: 'settings.tabs.costCenters' },
] as const;

/** Dedicated settings subroutes (hubs live outside tab panels). */
const SETTINGS_SUBROUTES: {
  to: string;
  labelKey: string;
  ownerOnly?: boolean;
  requiredModule?: string;
  anyOfModules?: readonly string[];
}[] = [
  {
    to: '/settings/integrations',
    labelKey: 'settings.subroutes.integrationsHub',
    anyOfModules: ['SHOPIFY', 'ACCOUNTING'],
  },
  { to: '/settings/billing', labelKey: 'settings.subroutes.billing' },
  {
    to: '/settings/fintech',
    labelKey: 'settings.subroutes.fintech',
    ownerOnly: true,
    requiredModule: 'FINTECH',
  },
];

type TabId = (typeof TABS)[number]['id'];

const SYNC_STATUS_STYLES: Record<string, string> = {
  SYNCED: 'bg-success/20 text-success',
  PENDING: 'bg-warning/20 text-warning',
  FAILED: 'bg-danger/20 text-danger',
  SKIPPED: 'bg-surface-overlay text-text-muted',
  ACTIVE: 'bg-success/20 text-success',
  DISCONNECTED: 'bg-surface-overlay text-text-muted',
  INVITED: 'bg-warning/20 text-warning',
};

function statusChip(status?: string) {
  const normalized = status ?? 'UNKNOWN';
  return (
    <span
      className={cn(
        'rounded-full px-2 py-0.5 text-xs font-medium',
        SYNC_STATUS_STYLES[normalized] ?? 'bg-surface-overlay text-text-muted'
      )}
    >
      {normalized}
    </span>
  );
}

/* ------------------------------- Settings map hook ------------------------------ */

function useTenantSettings() {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['settings'],
    queryFn: async () => (await apiClient.get<TenantSettingsMap>('/api/v1/settings')).data,
    retry: false,
  });

  const patchMutation = useMutation({
    mutationFn: async (patch: TenantSettingsMap) => {
      await apiClient.patch('/api/v1/settings', patch);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
  });

  return { ...query, patch: patchMutation };
}

function SavedNote({ show }: { show: boolean }) {
  const { t } = useTranslation();
  if (!show) return null;
  return <span className="text-sm text-success">{t('settings.saved')}</span>;
}

/* ------------------------------------ Profile ----------------------------------- */

/** Admin Settings → Profile: company prefs only. Personal data lives at /settings/profile. */
function ProfileTab() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const setLanguage = usePreferencesStore((s) => s.setLanguage);
  const settings = useTenantSettings();
  const [currency, setCurrency] = useState('');
  const [localeLanguage, setLocaleLanguage] = useState('en');

  useEffect(() => {
    if (settings.data && typeof settings.data.currency === 'string') {
      setCurrency(settings.data.currency);
    }
    if (settings.data && typeof settings.data.locale_language === 'string' && settings.data.locale_language) {
      const raw = settings.data.locale_language.toLowerCase();
      setLocaleLanguage(raw.startsWith('es') ? 'es' : raw.startsWith('fr') ? 'fr' : 'en');
    }
  }, [settings.data]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title={t('settings.personalMovedTitle')}
          description={t('settings.personalMovedDescription')}
        />
        <p className="mb-3 text-sm text-text-muted">{t('settings.personalMovedHint')}</p>
        <Link
          to="/settings/profile"
          data-testid="open-personal-profile"
          className="inline-flex"
        >
          <Button type="button" size="sm" variant="secondary">
            {t('settings.openPersonal')}
          </Button>
        </Link>
      </Card>

      <Card>
        <CardHeader
          title={t('settings.companyPreferences')}
          description={t('settings.companyPreferencesDescription')}
        />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            settings.patch.mutate(
              { currency, locale_language: localeLanguage },
              {
                onSuccess: () => {
                  setLanguage(normalizeLanguage(localeLanguage));
                  toast(t('settings.workspaceLanguageUpdated'), { tone: 'success' });
                },
              },
            );
          }}
          className="grid grid-cols-1 gap-4 md:grid-cols-2"
        >
          <Select
            label={t('settings.workspaceLanguage')}
            value={localeLanguage}
            onChange={(e) => setLocaleLanguage(e.target.value)}
            data-testid="org-locale-language"
          >
            <option value="en">{t('languages.en')}</option>
            <option value="es">{t('languages.es')}</option>
            <option value="fr">{t('languages.fr')}</option>
          </Select>
          <Select
            label={t('settings.baseCurrency')}
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            data-testid="org-base-currency"
          >
            {['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'MXN'].map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
          <div className="flex items-center gap-3 md:col-span-2">
            <Button type="submit" loading={settings.patch.isPending} data-testid="save-workspace-language">
              {t('settings.saveChanges')}
            </Button>
            <SavedNote show={settings.patch.isSuccess && !settings.patch.isPending} />
          </div>
        </form>
      </Card>
    </div>
  );
}

/* ------------------------------------- Users ------------------------------------ */

type PendingInvitation = {
  id: string;
  email: string;
  role: string;
  roles?: string[];
  expiresAt: string;
};

function inviteErrorMessage(err: unknown): string {
  const detail = (err as { response?: { data?: { detail?: string; title?: string } } })?.response
    ?.data;
  if (detail?.detail) return detail.detail;
  if (detail?.title === 'INVITE_PENDING') {
    return 'An open invitation already exists for this email.';
  }
  if (detail?.title === 'USER_EXISTS') {
    return 'A user with this email already exists.';
  }
  return 'Could not send the invitation. Check the email and try again.';
}

function InviteUserModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const canManageOrg = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN'));
  const [email, setEmail] = useState('');
  const [roleIds, setRoleIds] = useState<string[]>(['VIEWER']);
  const [customerId, setCustomerId] = useState('');
  const [error, setError] = useState('');

  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: async () => (await apiClient.get<Customer[]>('/api/v1/customers')).data,
    enabled: open && roleIds.includes('B2B_CUSTOMER'),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      const roleError = requireAtLeastOneRole(roleIds);
      if (roleError) {
        throw new Error(roleError);
      }
      return userApi.create({
        email: email.trim(),
        roleIds,
        customerId: roleIds.includes('B2B_CUSTOMER') ? customerId : undefined,
      });
    },
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['pending-invitations'] });
      toast(`Invitation sent to ${(data as { email?: string }).email ?? email}`, { tone: 'success' });
      setEmail('');
      setRoleIds(['VIEWER']);
      setCustomerId('');
      setError('');
      onClose();
    },
    onError: (err) => setError(err instanceof Error && err.message === 'Select at least one role'
      ? err.message
      : inviteErrorMessage(err)),
  });

  return (
    <Modal open={open} onClose={onClose} title="Invite user" description="They will receive a link to join this workspace">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const roleError = requireAtLeastOneRole(roleIds);
          if (roleError) {
            setError(roleError);
            return;
          }
          setError('');
          mutation.mutate();
        }}
        className="space-y-6"
        data-testid="invite-user-modal"
      >
        <section className="space-y-3">
          <h3 className="text-sm font-semibold text-text">Personal information</h3>
          <p className="text-xs text-text-muted">
            Invitees set their own password, address, and avatar after accepting.
          </p>
          <Input
            id="invite-email"
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
            autoComplete="off"
          />
        </section>
        {canManageOrg && (
          <section className="space-y-3" data-testid="invite-org-scope">
            <h3 className="text-sm font-semibold text-text">Organizational scope</h3>
            <div className="grid grid-cols-1 gap-4">
              <RoleMultiSelect value={roleIds} onChange={setRoleIds} testId="invite-role-multiselect" />
              {roleIds.includes('B2B_CUSTOMER') && (
                <Select
                  id="invite-portal-customer"
                  label="Portal customer"
                  value={customerId}
                  onChange={(e) => setCustomerId(e.target.value)}
                  required
                >
                  <option value="" disabled>
                    Select the customer account…
                  </option>
                  {customers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </Select>
              )}
            </div>
          </section>
        )}
        {error && (
          <p className="text-sm text-danger" data-testid="invite-error">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} data-testid="invite-submit">
            Send invitation
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function UserDetailDrawer({
  user,
  open,
  onClose,
}: {
  user: TenantUser | null;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const canManageOrg = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN'));
  const [roleIds, setRoleIds] = useState<string[]>(['VIEWER']);
  const [department, setDepartment] = useState('');
  const [timezone, setTimezone] = useState('');
  const [locale, setLocale] = useState('en-US');
  const [shift, setShift] = useState('');
  const [assignedWarehouseId, setAssignedWarehouseId] = useState('');
  const [warehouseIds, setWarehouseIds] = useState<string[]>([]);
  const [error, setError] = useState('');

  const { data: warehouses = [] } = useQuery({
    queryKey: ['locations', 'warehouses-admin'],
    queryFn: async () => {
      const rows = (await apiClient.get<TenantLocation[]>('/api/v1/locations')).data;
      return rows.filter((l) => l.type === 'WAREHOUSE');
    },
    enabled: open,
  });

  useEffect(() => {
    if (!user) return;
    setRoleIds(user.roles.length > 0 ? [...user.roles] : ['VIEWER']);
    setDepartment(user.corporateDepartment ?? user.department ?? '');
    setTimezone(user.timezonePreference ?? '');
    setLocale(user.localeLanguage ?? 'en-US');
    setShift(user.shiftScheduleType ?? user.shiftSchedule ?? '');
    setAssignedWarehouseId(user.assignedWarehouseId ?? '');
    setWarehouseIds(user.warehouseIds ?? []);
    setError('');
  }, [user]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!user) return;
      const roleError = requireAtLeastOneRole(roleIds);
      if (roleError) {
        throw new Error(roleError);
      }
      await userApi.update(user.id, {
        roleIds,
        corporateDepartment: department || null,
        timezonePreference: timezone || null,
        localeLanguage: locale || null,
        shiftScheduleType: shift || null,
        assignedWarehouseId: assignedWarehouseId || null,
        clearAssignedWarehouse: !assignedWarehouseId,
        warehouseIds,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      toast('Access updated successfully', { tone: 'success' });
      onClose();
    },
      onError: (err) =>
        setError(err instanceof Error && err.message === 'Select at least one role'
          ? err.message
          : 'Could not update organizational scope.'),
  });

  return (
    <RightPeekDrawer
      open={open}
      onClose={onClose}
      title={user ? `Edit access — ${user.displayName}` : 'Edit access'}
      description="Organizational scope is admin-only and audited"
      width="lg"
    >
      {user && (
        <form
          data-testid="user-detail-drawer"
          className="space-y-6"
          onSubmit={(e) => {
            e.preventDefault();
            if (!canManageOrg) return;
            const roleError = requireAtLeastOneRole(roleIds);
            if (roleError) {
              setError(roleError);
              return;
            }
            saveMutation.mutate();
          }}
        >
          <section className="space-y-2">
            <h3 className="text-sm font-semibold text-text">Personal information</h3>
            <p className="text-xs text-text-muted">Editable by the user on their personal profile.</p>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <Input label="Display name" value={user.displayName} disabled />
              <Input label="Email" value={user.email} disabled />
            </div>
          </section>

          {canManageOrg ? (
            <section className="space-y-3" data-testid="org-scope-section">
              <h3 className="text-sm font-semibold text-text">Organizational scope</h3>
              <RoleMultiSelect
                value={roleIds}
                onChange={setRoleIds}
                exclude={['B2B_CUSTOMER']}
                includeCodes={user.roles.includes('OWNER') ? ['OWNER'] : []}
              />
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <Input
                  label="Department"
                  value={department}
                  onChange={(e) => setDepartment(e.target.value)}
                  placeholder="e.g. Outbound"
                />
                <Input
                  label="Timezone"
                  value={timezone}
                  onChange={(e) => setTimezone(e.target.value)}
                  placeholder="America/Chicago"
                />
                <Select label="Locale" value={locale} onChange={(e) => setLocale(e.target.value)}>
                  {['en-US', 'en-GB', 'es-MX', 'fr-CA'].map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </Select>
                <Select label="Shift schedule" value={shift} onChange={(e) => setShift(e.target.value)}>
                  <option value="">Unset</option>
                  <option value="DAY">Day</option>
                  <option value="NIGHT">Night</option>
                  <option value="WEEKEND">Weekend</option>
                </Select>
                <Select
                  label="Default assigned warehouse"
                  value={assignedWarehouseId}
                  onChange={(e) => setAssignedWarehouseId(e.target.value)}
                >
                  <option value="">None</option>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.code} — {w.name}
                    </option>
                  ))}
                </Select>
              </div>
              <fieldset data-testid="warehouse-multiselect">
                <legend className="mb-2 text-sm font-medium text-text">Warehouse access (LBAC)</legend>
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  {warehouses.map((w) => {
                    const checked = warehouseIds.includes(w.id);
                    return (
                      <label key={w.id} className="flex items-center gap-2 text-sm text-text">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            setWarehouseIds((prev) =>
                              e.target.checked
                                ? [...prev, w.id]
                                : prev.filter((id) => id !== w.id),
                            );
                          }}
                        />
                        {w.code} — {w.name}
                      </label>
                    );
                  })}
                </div>
              </fieldset>
            </section>
          ) : (
            <p className="text-sm text-text-muted">You do not have permission to edit organizational scope.</p>
          )}

          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            {canManageOrg && (
              <Button type="submit" loading={saveMutation.isPending} data-testid="save-org-scope">
                Save access
              </Button>
            )}
          </div>

          {canManageOrg && (
            <div className="border-t border-border pt-6">
              <ActivityTimeline entityType="USER" entityId={user.id} enabled={open} />
            </div>
          )}
        </form>
      )}
    </RightPeekDrawer>
  );
}

function UsersTab() {
  const queryClient = useQueryClient();
  const currentUser = useSessionStore((s) => s.user);
  const canManageOrg = useSessionStore((s) => s.hasRole('OWNER', 'ADMIN'));
  const [inviteOpen, setInviteOpen] = useState(false);
  const [editUser, setEditUser] = useState<TenantUser | null>(null);

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.list(),
    retry: false,
  });

  const { data: pendingInvites = [] } = useQuery({
    queryKey: ['pending-invitations'],
    queryFn: async () =>
      (await apiClient.get<PendingInvitation[]>('/api/v1/users/invitations')).data,
    enabled: canManageOrg,
    retry: false,
  });

  const { sort: inviteSort, toggle: toggleInviteSort, sorted: sortedInvites } = useClientSort(
    pendingInvites,
    {
      email: (inv) => inv.email,
      role: (inv) => (inv.roles?.length ? inv.roles : [inv.role]).join(','),
      status: () => 'PENDING',
      expires: (inv) => (inv.expiresAt ? new Date(inv.expiresAt) : null),
    },
  );

  const { sort: userSort, toggle: toggleUserSort, sorted: sortedUsers } = useClientSort(users, {
    user: (u) => u.displayName || u.email,
    role: (u) => u.roles.join(','),
    department: (u) => u.corporateDepartment ?? u.department ?? '',
    status: (u) => u.status,
  });

  const { toast } = useToast();

  const deactivateMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/users/${id}/deactivate`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  const resendInvitationMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/office/invitations/${id}/resend`);
    },
    onSuccess: () => {
      toast('Reminder email dispatched successfully', { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['invitations'] });
      void queryClient.invalidateQueries({ queryKey: ['pending-invitations'] });
    },
    onError: () => {
      toast('Could not send invitation reminder', { tone: 'danger' });
    },
  });

  return (
    <Card className="min-w-0 max-w-full">
      <CardHeader
        title="Users & invitations"
        description="Invite team members and manage organizational access"
        action={
          canManageOrg ? (
            <Button size="sm" onClick={() => setInviteOpen(true)} data-testid="invite-user-button">
              <UserPlus className="h-4 w-4" />
              Invite user
            </Button>
          ) : undefined
        }
      />
      {isLoading ? (
        <TableSkeleton rows={5} cols={4} />
      ) : (
        <div className="space-y-6">
          {canManageOrg && pendingInvites.length > 0 && (
            <div data-testid="pending-invitations">
              <h3 className="mb-2 text-sm font-semibold text-text">Pending invitations</h3>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead sortable sortKey="email" sort={inviteSort} onSort={toggleInviteSort}>
                      Email
                    </TableHead>
                    <TableHead sortable sortKey="role" sort={inviteSort} onSort={toggleInviteSort}>
                      Role
                    </TableHead>
                    <TableHead sortable sortKey="status" sort={inviteSort} onSort={toggleInviteSort}>
                      Status
                    </TableHead>
                    <TableHead sortable sortKey="expires" sort={inviteSort} onSort={toggleInviteSort}>
                      Expires
                    </TableHead>
                    <TableHead align="right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sortedInvites.map((inv) => (
                    <TableRow key={inv.id} data-testid={`pending-invite-${inv.email}`}>
                      <TableCell>
                        <p className="font-medium text-text">{inv.email}</p>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {(inv.roles?.length ? inv.roles : [inv.role]).map((code) => (
                            <StatusBadge key={code} status={formatRoleLabel(code)} />
                          ))}
                        </div>
                      </TableCell>
                      <TableCell>{statusChip('PENDING')}</TableCell>
                      <TableCell>
                        <span className="text-sm text-text-muted" data-testid={`pending-invite-expires-${inv.id}`}>
                          {inv.expiresAt ? new Date(inv.expiresAt).toLocaleDateString() : '—'}
                        </span>
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          className="h-8 shrink-0 whitespace-nowrap"
                          data-testid={`resend-invite-${inv.id}`}
                          loading={
                            resendInvitationMutation.isPending &&
                            resendInvitationMutation.variables === inv.id
                          }
                          onClick={() => resendInvitationMutation.mutate(inv.id)}
                        >
                          <Mail className="h-4 w-4" />
                          Send Reminder
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead sortable sortKey="user" sort={userSort} onSort={toggleUserSort}>
                  User
                </TableHead>
                <TableHead sortable sortKey="role" sort={userSort} onSort={toggleUserSort}>
                  Role
                </TableHead>
                <TableHead sortable sortKey="department" sort={userSort} onSort={toggleUserSort}>
                  Department
                </TableHead>
                <TableHead sortable sortKey="status" sort={userSort} onSort={toggleUserSort}>
                  Status
                </TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedUsers.map((u) => {
                const isSelf = u.id === currentUser?.id;
                const isOwner = u.roles.includes('OWNER');
                return (
                  <TableRow key={u.id}>
                    <TableCell>
                      <p className="font-medium text-text">{u.displayName}</p>
                      <p className="text-xs text-text-muted">{u.email}</p>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1" data-testid={`user-roles-${u.id}`}>
                        {u.roles.length > 0 ? (
                          u.roles.map((code) => <StatusBadge key={code} status={code} />)
                        ) : (
                          <span className="text-sm text-text-muted">—</span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <span className="text-sm">{u.corporateDepartment ?? u.department ?? '—'}</span>
                    </TableCell>
                    <TableCell>{statusChip(u.status)}</TableCell>
                    <TableCell align="right" className="w-px whitespace-nowrap">
                      <div className="flex flex-nowrap items-center justify-end gap-2">
                        {canManageOrg && (
                          <Button
                            variant="secondary"
                            size="sm"
                            className="h-8 shrink-0 whitespace-nowrap"
                            data-testid={`edit-access-${u.id}`}
                            onClick={() => setEditUser(u)}
                          >
                            <Pencil className="h-3.5 w-3.5" aria-hidden />
                            Edit access
                          </Button>
                        )}
                        {canManageOrg && !isSelf && !isOwner && u.status === 'ACTIVE' && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-8 shrink-0 whitespace-nowrap"
                            loading={deactivateMutation.isPending}
                            onClick={() => deactivateMutation.mutate(u.id)}
                          >
                            Deactivate
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>

          {canManageOrg && (
            <div className="min-w-0 max-w-full pt-2">
              <RolePermissionsMatrix />
            </div>
          )}
        </div>
      )}
      <InviteUserModal open={inviteOpen} onClose={() => setInviteOpen(false)} />
      <UserDetailDrawer user={editUser} open={!!editUser} onClose={() => setEditUser(null)} />
    </Card>
  );
}

/* ---------------------------------- Warehouses ---------------------------------- */

function WarehousesTab() {
  const navigate = useNavigate();
  const [ssid, setSsid] = useState('Warehouse-Floor-A');
  const [ruleLocationId, setRuleLocationId] = useState('');
  const queryClient = useQueryClient();

  const { data: locations = [], isLoading } = useQuery({
    queryKey: ['locations'],
    queryFn: async () => (await apiClient.get<TenantLocation[]>('/api/v1/locations')).data,
    retry: false,
  });

  const warehouses = locations.filter((l) => l.type === 'WAREHOUSE' || l.type === 'VEHICLE');

  const { data: contextRules = [] } = useQuery({
    queryKey: ['warehouse-context-rules'],
    queryFn: async () =>
      (
        await apiClient.get<
          Array<{
            id: string;
            locationId: string;
            matchType: string;
            ssid?: string;
            latitude?: number;
            longitude?: number;
            radiusMeters?: number;
            priority: number;
            enabled: boolean;
            label?: string;
          }>
        >('/api/v1/warehouse-context-rules')
      ).data,
    retry: false,
  });

  const createRule = useMutation({
    mutationFn: async () => {
      const locationId = ruleLocationId || warehouses[0]?.id;
      if (!locationId || !ssid.trim()) throw new Error('SSID and warehouse required');
      await apiClient.post('/api/v1/warehouse-context-rules', {
        locationId,
        matchType: 'WIFI_SSID',
        ssid: ssid.trim(),
        priority: 10,
        enabled: true,
        label: `SSID ${ssid.trim()}`,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['warehouse-context-rules'] });
    },
  });

  const deleteRule = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/warehouse-context-rules/${id}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['warehouse-context-rules'] });
    },
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Warehouse layout"
          description="Spatial hierarchy — warehouses, zones, aisles, and bins"
        />
        {isLoading ? (
          <TableSkeleton rows={6} cols={3} />
        ) : (
          <WarehouseVisualizer
            locations={locations}
            onAddWarehouse={() => navigate('/warehouses/add')}
          />
        )}
      </Card>

      <Card data-testid="floor-hardware-compat">
        <CardHeader
          title="Floor hardware compatibility"
          description="Mobile web (PWA) targets the scanners operators actually carry"
        />
        <ul className="list-disc space-y-2 pl-5 text-sm text-text-muted">
          <li>
            <span className="font-medium text-text">Zebra TC / MC series</span> — DataWedge keyboard
            wedge or intent broadcast (built into <code className="text-text">useBarcodeScanner</code>
            ).
          </li>
          <li>
            <span className="font-medium text-text">Honeywell CT / CK / Granit</span> — ScanPal /
            HID wedge and intent receivers for rugged Android.
          </li>
          <li>
            <span className="font-medium text-text">USB / Bluetooth HID wedges</span> — corded
            presentation scanners at pack stations (DS36xx / Voyager class).
          </li>
          <li>
            Touch targets and scan feedback stay on the warehouse theme; office Settings uses
            widescreen layouts without floor-scanner inflation on 1080p desktops.
          </li>
        </ul>
      </Card>

      <Card>
        <CardHeader
          title="Terminal context gate"
          description="Auto-assign warehouse from Wi-Fi SSID or GPS geofence — hides the switcher when matched"
        />
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <label className="text-sm" htmlFor="terminal-context-ssid">
            <span className="mb-1 block text-text-muted">Wi-Fi SSID</span>
            <input
              id="terminal-context-ssid"
              name="terminalContextSsid"
              className="h-9 w-full rounded-md border border-border bg-surface-raised px-2 text-sm"
              value={ssid}
              onChange={(e) => setSsid(e.target.value)}
              aria-label="Wi-Fi SSID"
            />
          </label>
          <label className="text-sm" htmlFor="terminal-context-warehouse">
            <span className="mb-1 block text-text-muted">Warehouse</span>
            <select
              id="terminal-context-warehouse"
              name="terminalContextWarehouse"
              className="h-9 w-full rounded-md border border-border bg-surface-raised px-2 text-sm"
              value={ruleLocationId || warehouses[0]?.id || ''}
              onChange={(e) => setRuleLocationId(e.target.value)}
              aria-label="Context warehouse"
            >
              {warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}
                </option>
              ))}
            </select>
          </label>
          <div className="flex items-end">
            <Button
              size="sm"
              disabled={createRule.isPending || warehouses.length === 0}
              onClick={() => createRule.mutate()}
            >
              Add SSID rule
            </Button>
          </div>
        </div>
        {contextRules.length === 0 ? (
          <p className="text-sm text-text-muted">
            No rules yet. Map floor WLAN SSIDs or yard geofences so handhelds lock to the right facility on boot.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead>Match</TableHead>
                <TableHead>Warehouse</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {contextRules.map((rule) => {
                const loc = locations.find((l) => l.id === rule.locationId);
                return (
                  <TableRow key={rule.id}>
                    <TableCell>{rule.matchType}</TableCell>
                    <TableCell mono>
                      {rule.matchType === 'WIFI_SSID'
                        ? rule.ssid
                        : `${rule.latitude}, ${rule.longitude} ±${rule.radiusMeters}m`}
                    </TableCell>
                    <TableCell>{loc?.name ?? rule.locationId}</TableCell>
                    <TableCell>{rule.priority}</TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => deleteRule.mutate(rule.id)}
                      >
                        Remove
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}

/* --------------------------------- Inventory rules ------------------------------ */

function InventoryRulesTab() {
  const settings = useTenantSettings();
  const queryClient = useQueryClient();
  const [allowNegative, setAllowNegative] = useState(false);
  const [allowBlindReceiving, setAllowBlindReceiving] = useState(false);
  const [overReceiptTolerance, setOverReceiptTolerance] = useState('0');
  const [barcodePrefix, setBarcodePrefix] = useState('');
  const [reorderPoint, setReorderPoint] = useState('');
  const [costingMethod, setCostingMethod] = useState('MOVING_AVERAGE');
  const [taxName, setTaxName] = useState('');
  const [taxRate, setTaxRate] = useState('');
  const [schemeName, setSchemeName] = useState('');
  const [schemeInclusive, setSchemeInclusive] = useState(false);
  const [primaryRateName, setPrimaryRateName] = useState('Primary');
  const [primaryRate, setPrimaryRate] = useState('0.05');
  const [secondaryRateName, setSecondaryRateName] = useState('Secondary');
  const [secondaryRate, setSecondaryRate] = useState('0.02');

  const { data: taxRates = [] } = useQuery({
    queryKey: ['tax-rates'],
    queryFn: async () => (await apiClient.get<TaxRate[]>('/api/v1/settings/taxes')).data,
    retry: false,
  });

  const { data: taxSchemes = [] } = useQuery({
    queryKey: ['tax-schemes'],
    queryFn: async () => (await apiClient.get<TaxScheme[]>('/api/v1/settings/tax-schemes')).data,
    retry: false,
  });

  const createTaxMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/taxes', {
        name: taxName,
        rate: Number(taxRate),
        isDefault: taxRates.length === 0,
      });
    },
    onSuccess: () => {
      setTaxName('');
      setTaxRate('');
      void queryClient.invalidateQueries({ queryKey: ['tax-rates'] });
    },
  });

  const deleteTaxMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/settings/taxes/${id}`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['tax-rates'] }),
  });

  const createSchemeMutation = useMutation({
    mutationFn: async () => {
      const rates = [
        { name: primaryRateName || 'Primary', rate: Number(primaryRate) || 0, sortOrder: 0 },
      ];
      if (secondaryRate && Number(secondaryRate) > 0) {
        rates.push({
          name: secondaryRateName || 'Secondary',
          rate: Number(secondaryRate),
          sortOrder: 1,
        });
      }
      await apiClient.post('/api/v1/settings/tax-schemes', {
        name: schemeName,
        taxInclusive: schemeInclusive,
        rates,
      });
    },
    onSuccess: () => {
      setSchemeName('');
      void queryClient.invalidateQueries({ queryKey: ['tax-schemes'] });
    },
  });

  const deactivateSchemeMutation = useMutation({
    mutationFn: async (scheme: TaxScheme) => {
      await apiClient.put(`/api/v1/settings/tax-schemes/${scheme.id}`, {
        active: false,
      });
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['tax-schemes'] }),
  });

  useEffect(() => {
    const s = settings.data;
    if (!s) return;
    setAllowNegative(Boolean(s.allow_negative_inventory));
    setAllowBlindReceiving(Boolean(s.allow_blind_receiving));
    if (s.over_receipt_tolerance_percent != null) {
      setOverReceiptTolerance(String(s.over_receipt_tolerance_percent));
    }
    if (typeof s.barcode_prefix_strip === 'string') setBarcodePrefix(s.barcode_prefix_strip);
    if (s.default_reorder_point != null) setReorderPoint(String(s.default_reorder_point));
    if (typeof s.costing_method === 'string') setCostingMethod(s.costing_method);
  }, [settings.data]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title="Inventory rules" description="Stock and barcode settings" />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            settings.patch.mutate({
              allow_negative_inventory: allowNegative,
              allow_blind_receiving: allowBlindReceiving,
              over_receipt_tolerance_percent: Number(overReceiptTolerance) || 0,
              barcode_prefix_strip: barcodePrefix,
              default_reorder_point: reorderPoint ? Number(reorderPoint) : 0,
              costing_method: costingMethod,
            });
          }}
          className="space-y-4"
        >
          <label className="flex items-center gap-3" htmlFor="inventory-allow-negative">
            <input
              id="inventory-allow-negative"
              name="allowNegativeInventory"
              type="checkbox"
              checked={allowNegative}
              onChange={(e) => setAllowNegative(e.target.checked)}
              className="h-4 w-4 rounded border-border accent-accent"
            />
            <span className="text-sm text-text">Allow negative inventory</span>
          </label>
          <label className="flex items-center gap-3" htmlFor="inventory-allow-blind-receiving">
            <input
              id="inventory-allow-blind-receiving"
              name="allowBlindReceiving"
              type="checkbox"
              checked={allowBlindReceiving}
              onChange={(e) => setAllowBlindReceiving(e.target.checked)}
              className="h-4 w-4 rounded border-border accent-accent"
            />
            <span className="text-sm text-text">Allow blind scan receiving (no PO)</span>
          </label>
          <Input
            label="Over-receipt tolerance (%)"
            type="number"
            min={0}
            max={100}
            step="0.01"
            value={overReceiptTolerance}
            onChange={(e) => setOverReceiptTolerance(e.target.value)}
          />
          <Input
            label="Barcode prefix to strip"
            value={barcodePrefix}
            onChange={(e) => setBarcodePrefix(e.target.value)}
            placeholder="e.g. ]C1"
          />
          <Input
            label="Default reorder point"
            type="number"
            min="0"
            value={reorderPoint}
            onChange={(e) => setReorderPoint(e.target.value)}
            placeholder="10"
          />
          <Select
            label="Costing method"
            value={costingMethod}
            onChange={(e) => setCostingMethod(e.target.value)}
          >
            <option value="MOVING_AVERAGE">Moving average</option>
            <option value="FIFO">FIFO</option>
          </Select>
          <p className="text-xs text-text-muted">
            Changing costing method queues an async append-only re-cost of avg_cost from ledger history.
          </p>
          <div className="flex items-center gap-3">
            <Button type="submit" loading={settings.patch.isPending}>
              Save rules
            </Button>
            <SavedNote show={settings.patch.isSuccess && !settings.patch.isPending} />
          </div>
        </form>
      </Card>

      <Card>
        <CardHeader
          title="Stacked tax schemes"
          description="Compound primary + secondary rates (Total tax = Σ P×Q×Rate_i)"
        />
        <form
          className="mb-4 space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            createSchemeMutation.mutate();
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Scheme name"
              value={schemeName}
              onChange={(e) => setSchemeName(e.target.value)}
              placeholder="CA GST+PST"
              required
            />
            <label className="flex items-end gap-3 pb-2" htmlFor="tax-scheme-inclusive">
              <input
                id="tax-scheme-inclusive"
                name="taxInclusivePricing"
                type="checkbox"
                checked={schemeInclusive}
                onChange={(e) => setSchemeInclusive(e.target.checked)}
                className="h-4 w-4 rounded border-border accent-accent"
              />
              <span className="text-sm text-text">Tax inclusive pricing</span>
            </label>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Primary rate name"
              value={primaryRateName}
              onChange={(e) => setPrimaryRateName(e.target.value)}
            />
            <Input
              label="Primary rate (decimal)"
              type="number"
              step="0.0001"
              min="0"
              value={primaryRate}
              onChange={(e) => setPrimaryRate(e.target.value)}
              required
            />
            <Input
              label="Secondary rate name"
              value={secondaryRateName}
              onChange={(e) => setSecondaryRateName(e.target.value)}
            />
            <Input
              label="Secondary rate (decimal)"
              type="number"
              step="0.0001"
              min="0"
              value={secondaryRate}
              onChange={(e) => setSecondaryRate(e.target.value)}
            />
          </div>
          <Button type="submit" loading={createSchemeMutation.isPending}>
            <Plus className="h-4 w-4" />
            Add stacked scheme
          </Button>
        </form>
        {taxSchemes.length === 0 ? (
          <p className="text-sm text-text-muted">No stacked tax schemes yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Rates</TableHead>
                <TableHead>Inclusive</TableHead>
                <TableHead>Status</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {taxSchemes.map((scheme) => (
                <TableRow key={scheme.id}>
                  <TableCell>{scheme.name}</TableCell>
                  <TableCell>
                    {scheme.rates
                      .map((r) => `${r.name} ${(Number(r.rate) * 100).toFixed(2)}%`)
                      .join(' + ') || '—'}
                  </TableCell>
                  <TableCell>{scheme.taxInclusive ? 'Yes' : 'No'}</TableCell>
                  <TableCell>{statusChip(scheme.active ? 'ACTIVE' : 'SKIPPED')}</TableCell>
                  <TableCell align="right">
                    {scheme.active && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => deactivateSchemeMutation.mutate(scheme)}
                        loading={deactivateSchemeMutation.isPending}
                      >
                        Deactivate
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card>
        <CardHeader title="Legacy single tax rates" description="Default tax applied to new sales order lines" />
        <form
          className="mb-4 grid gap-4 sm:grid-cols-3"
          onSubmit={(e) => {
            e.preventDefault();
            createTaxMutation.mutate();
          }}
        >
          <Input label="Name" value={taxName} onChange={(e) => setTaxName(e.target.value)} required />
          <Input
            label="Rate (decimal)"
            type="number"
            step="0.0001"
            min="0"
            max="1"
            value={taxRate}
            onChange={(e) => setTaxRate(e.target.value)}
            placeholder="0.0825"
            required
          />
          <div className="flex items-end">
            <Button type="submit" loading={createTaxMutation.isPending}>
              <Plus className="h-4 w-4" />
              Add rate
            </Button>
          </div>
        </form>
        {taxRates.length === 0 ? (
          <p className="text-sm text-text-muted">No tax rates configured.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Rate</TableHead>
                <TableHead>Default</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {taxRates.map((rate) => (
                <TableRow key={rate.id}>
                  <TableCell>{rate.name}</TableCell>
                  <TableCell>{(rate.rate * 100).toFixed(2)}%</TableCell>
                  <TableCell>{rate.isDefault ? statusChip('ACTIVE') : statusChip('SKIPPED')}</TableCell>
                  <TableCell align="right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteTaxMutation.mutate(rate.id)}
                      loading={deleteTaxMutation.isPending}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}

/* ----------------------------------- Documents ---------------------------------- */

function DocumentsTab() {
  const settings = useTenantSettings();
  const [invoiceFormat, setInvoiceFormat] = useState('INV-{YYYY}-{seq:5}');
  const [soFormat, setSoFormat] = useState('SO-{YYYY}-{seq:5}');
  const [poFormat, setPoFormat] = useState('PO-{YYYY}-{seq:5}');
  const [skuTemplate, setSkuTemplate] = useState('SKU-{PREFIX}-{ID:5}');
  const [barcodeTemplate, setBarcodeTemplate] = useState('BC-{ID:8}');
  const [skuPrefix, setSkuPrefix] = useState('INV');

  useEffect(() => {
    const s = settings.data;
    if (!s) return;
    if (typeof s.invoice_number_format === 'string') setInvoiceFormat(s.invoice_number_format);
    if (typeof s.sales_order_number_format === 'string') setSoFormat(s.sales_order_number_format);
    if (typeof s.purchase_order_number_format === 'string') setPoFormat(s.purchase_order_number_format);
    if (typeof s.sku_template === 'string') setSkuTemplate(s.sku_template);
    if (typeof s.barcode_template === 'string') setBarcodeTemplate(s.barcode_template);
    if (typeof s.sku_prefix === 'string') setSkuPrefix(s.sku_prefix);
  }, [settings.data]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title="Document numbering" description="Invoice and order number formats" />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            settings.patch.mutate({
              invoice_number_format: invoiceFormat,
              sales_order_number_format: soFormat,
              purchase_order_number_format: poFormat,
            });
          }}
          className="space-y-4"
        >
          <Input label="Invoice format" value={invoiceFormat} onChange={(e) => setInvoiceFormat(e.target.value)} />
          <Input label="Sales order format" value={soFormat} onChange={(e) => setSoFormat(e.target.value)} />
          <Input label="Purchase order format" value={poFormat} onChange={(e) => setPoFormat(e.target.value)} />
          <div className="flex items-center gap-3">
            <Button type="submit" loading={settings.patch.isPending}>
              Save formats
            </Button>
            <SavedNote show={settings.patch.isSuccess && !settings.patch.isPending} />
          </div>
        </form>
      </Card>

      <Card>
        <CardHeader
          title="SKU & barcode masks"
          description="Auto-mint templates when SKU is omitted on variant create. Tokens: {PREFIX}, {YYYY}, {ID:N}"
        />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            settings.patch.mutate({
              sku_template: skuTemplate,
              barcode_template: barcodeTemplate,
              sku_prefix: skuPrefix,
            });
          }}
          className="space-y-4"
        >
          <Input
            label="SKU prefix"
            value={skuPrefix}
            onChange={(e) => setSkuPrefix(e.target.value)}
            placeholder="INV"
          />
          <Input
            label="SKU template"
            value={skuTemplate}
            onChange={(e) => setSkuTemplate(e.target.value)}
            placeholder="SKU-{PREFIX}-{ID:5}"
          />
          <Input
            label="Barcode template"
            value={barcodeTemplate}
            onChange={(e) => setBarcodeTemplate(e.target.value)}
            placeholder="BC-{ID:8}"
          />
          <div className="flex items-center gap-3">
            <Button type="submit" loading={settings.patch.isPending}>
              Save masks
            </Button>
            <SavedNote show={settings.patch.isSuccess && !settings.patch.isPending} />
          </div>
        </form>
      </Card>
    </div>
  );
}

/* ------------------------------------ Security & SSO ----------------------------------- */

function SecuritySsoTab() {
  return <TenantSecuritySettings />;
}

/* ------------------------------ Financial reconciliation -------------------------- */

function ReconciliationTab() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['reports', 'reconciliation'],
    queryFn: async () => {
      const res = await apiClient.get<ReconciliationReport>('/api/v1/reports/reconciliation');
      return res.data;
    },
    retry: false,
  });

  if (isLoading) return <TableSkeleton rows={4} cols={2} />;
  if (isError || !data) {
    return (
      <Card>
        <p className="text-sm text-danger">Unable to load reconciliation report.</p>
        <Button className="mt-3" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
      </Card>
    );
  }

  const drift = Number(data.driftAmount);
  const hasDrift = Math.abs(drift) > 0.01;

  return (
    <div className="space-y-6" data-testid="reconciliation-report">
      <Card>
        <CardHeader title="Financial truth" description="Physical inventory vs accounting sync" />
        <div className="grid gap-4 sm:grid-cols-3">
          <div className="rounded-lg border border-border p-4">
            <p className="text-xs text-text-muted">Physical inventory value</p>
            <p
              className="mt-1 font-mono text-xl font-semibold text-text"
              data-testid="reconciliation-physical-value"
            >
              {Number(data.physicalInventoryValue).toLocaleString(undefined, {
                style: 'currency',
                currency: data.currency,
              })}
            </p>
          </div>
          <div className="rounded-lg border border-border p-4">
            <p className="text-xs text-text-muted">Accounting mapped value</p>
            <p className="mt-1 font-mono text-xl font-semibold text-text">
              {Number(data.accountingInventoryValue).toLocaleString(undefined, {
                style: 'currency',
                currency: data.currency,
              })}
            </p>
          </div>
          <div
            className={cn(
              'rounded-lg border p-4',
              hasDrift ? 'border-warning bg-warning/10' : 'border-success bg-success/10'
            )}
          >
            <p className="text-xs text-text-muted">Drift</p>
            <p className="mt-1 font-mono text-xl font-semibold text-text">
              {drift.toLocaleString(undefined, { style: 'currency', currency: data.currency })}
            </p>
          </div>
        </div>
        <p className="mt-4 text-sm text-text-muted">
          {data.mappedAccounts} inventory account mapping{data.mappedAccounts === 1 ? '' : 's'}{' '}
          configured.
        </p>
      </Card>

      <Card>
        <CardHeader title="Sync drift log" description="Failed accounting sync attempts" />
        {data.syncDrifts.length === 0 ? (
          <p className="text-sm text-text-muted">No sync failures — books are aligned.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Error</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.syncDrifts.map((item, i) => (
                <TableRow key={`${item.entityId}-${i}`}>
                  <TableCell>{item.system}</TableCell>
                  <TableCell mono>{item.entityType}</TableCell>
                  <TableCell>{item.status}</TableCell>
                  <TableCell className="text-sm text-text-muted">{item.message ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}

interface ReconciliationReport {
  physicalInventoryValue: number;
  accountingInventoryValue: number;
  driftAmount: number;
  currency: string;
  mappedAccounts: number;
  syncDrifts: { system: string; entityType: string; entityId: string; status: string; message?: string }[];
}

/* ----------------------------- Operations console ----------------------------- */

function OperationsSettingsPanel() {
  const settings = useTenantSettings();
  const [waveMaxLines, setWaveMaxLines] = useState('40');
  const [waveMaxOrders, setWaveMaxOrders] = useState('12');
  const [overReceivePct, setOverReceivePct] = useState('0');
  const [allowOverReceiving, setAllowOverReceiving] = useState(false);

  useEffect(() => {
    if (!settings.data) return;
    const d = settings.data as Record<string, unknown>;
    setWaveMaxLines(String(d.picking_wave_max_lines ?? 40));
    setWaveMaxOrders(String(d.picking_wave_max_orders ?? 12));
    setOverReceivePct(String(d.over_receipt_tolerance_percent ?? 0));
    setAllowOverReceiving(Boolean(d.allow_over_receiving));
  }, [settings.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch('/api/v1/settings', {
        picking_wave_max_lines: Number(waveMaxLines) || 0,
        picking_wave_max_orders: Number(waveMaxOrders) || 0,
        over_receipt_tolerance_percent: Number(overReceivePct) || 0,
        allow_over_receiving: allowOverReceiving,
      });
      await apiClient.post('/api/v1/settings/cache/flush');
    },
    onSuccess: () => void settings.refetch(),
  });

  return (
    <Card data-testid="operations-settings-panel">
      <CardHeader
        title="Operations policies"
        description="Persisted in tenant_settings JSONB — Redis cache flush on save"
      />
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          saveMutation.mutate();
        }}
      >
        <Input
          label="Picking wave max lines"
          type="number"
          min={1}
          value={waveMaxLines}
          onChange={(e) => setWaveMaxLines(e.target.value)}
          data-testid="ops-wave-max-lines"
        />
        <Input
          label="Picking wave max orders"
          type="number"
          min={1}
          value={waveMaxOrders}
          onChange={(e) => setWaveMaxOrders(e.target.value)}
        />
        <Input
          label="Over-receive tolerance %"
          type="number"
          min={0}
          value={overReceivePct}
          onChange={(e) => setOverReceivePct(e.target.value)}
          data-testid="ops-over-receive-pct"
        />
        <label className="flex items-center gap-3 self-end pb-2">
          <input
            type="checkbox"
            checked={allowOverReceiving}
            onChange={(e) => setAllowOverReceiving(e.target.checked)}
            className="h-4 w-4 rounded border-border accent-accent"
          />
          <span className="text-sm text-text">Allow over-receiving</span>
        </label>
        <div className="sm:col-span-2">
          <Button type="submit" loading={saveMutation.isPending} data-testid="ops-settings-save">
            Save operations settings
          </Button>
        </div>
      </form>
    </Card>
  );
}

function OperationsConsoleTab() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const lastRequestId = useSessionStore((s) => s.lastRequestId);
  const [editing, setEditing] = useState<OutboxEventItem | null>(null);
  const [payloadText, setPayloadText] = useState('');

  const { data: outbox = [], isLoading: outboxLoading, refetch: refetchOutbox } = useQuery({
    queryKey: ['operations', 'outbox'],
    queryFn: async () => (await apiClient.get<OutboxEventItem[]>('/api/v1/operations/outbox/failed')).data,
    retry: false,
  });

  const { data: syncLogs = [], isLoading: syncLoading, refetch: refetchSync } = useQuery({
    queryKey: ['operations', 'sync-logs'],
    queryFn: async () => (await apiClient.get<SyncLog[]>('/api/v1/operations/sync-logs/failed')).data,
    retry: false,
  });

  const { data: alerts = [], refetch: refetchAlerts } = useQuery({
    queryKey: ['operations', 'alerts'],
    queryFn: async () => (await apiClient.get<PlatformAlertItem[]>('/api/v1/operations/alerts')).data,
    retry: false,
  });

  const retryOutbox = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/operations/outbox/${id}/retry`);
    },
    onSuccess: () => {
      toast('Outbox event re-queued', { tone: 'success' });
      void refetchOutbox();
      void queryClient.invalidateQueries({ queryKey: ['operations', 'audit'] });
    },
  });

  const savePayload = useMutation({
    mutationFn: async () => {
      if (!editing) return;
      const payload = JSON.parse(payloadText) as Record<string, unknown>;
      await apiClient.put(`/api/v1/operations/outbox/${editing.id}/payload`, { payload });
    },
    onSuccess: () => {
      toast('Payload updated and event re-queued', { tone: 'success' });
      setEditing(null);
      void refetchOutbox();
      void queryClient.invalidateQueries({ queryKey: ['operations', 'audit'] });
    },
    onError: () => toast('Could not save payload — check JSON', { tone: 'danger' }),
  });

  const retrySync = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/operations/sync-logs/${id}/retry`);
    },
    onSuccess: () => {
      toast('Sync log retried', { tone: 'success' });
      void refetchSync();
    },
  });

  const ackAlert = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/operations/alerts/${id}/acknowledge`);
    },
    onSuccess: () => {
      toast('Alert acknowledged', { tone: 'success' });
      void refetchAlerts();
    },
  });

  return (
    <div className="min-w-0 space-y-6" data-testid="operations-console">
      <OperationsSettingsPanel />
      <Card>
        <CardHeader
          title="Correlation"
          description="Use the latest request id when escalating incidents to support or logs."
        />
        <p className="font-mono text-sm text-text">
          Last X-Request-Id: {lastRequestId ?? '—'}
        </p>
      </Card>

      <Card>
        <CardHeader title="Platform alerts" description="High integration error rates in the last hour" />
        {alerts.length === 0 ? (
          <p className="text-sm text-text-muted">No open alerts.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Severity</TableHead>
                <TableHead>System</TableHead>
                <TableHead>Title</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {alerts.map((alert) => (
                <TableRow key={alert.id}>
                  <TableCell>{statusChip(alert.severity)}</TableCell>
                  <TableCell className="font-mono text-sm">{alert.sourceSystem ?? '—'}</TableCell>
                  <TableCell className="text-sm">{alert.title}</TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="secondary" onClick={() => ackAlert.mutate(alert.id)}>
                      Acknowledge
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card>
        <CardHeader title="Failed outbox events" description="Retry or edit payload for mapping conflicts" />
        {outboxLoading ? (
          <TableSkeleton rows={4} cols={4} />
        ) : outbox.length === 0 ? (
          <p className="text-sm text-text-muted">No failed outbox events.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Type</TableHead>
                <TableHead>Retries</TableHead>
                <TableHead>Error</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {outbox.map((event) => (
                <TableRow key={event.id}>
                  <TableCell className="font-mono text-sm">{event.eventType}</TableCell>
                  <TableCell className="tabular-nums">{event.retryCount}</TableCell>
                  <TableCell className="max-w-xs truncate text-sm text-danger">{event.lastError ?? '—'}</TableCell>
                  <TableCell className="space-x-2 text-right">
                    <Button size="sm" variant="secondary" onClick={() => retryOutbox.mutate(event.id)}>
                      Retry
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setEditing(event);
                        setPayloadText(JSON.stringify(event.payload ?? {}, null, 2));
                      }}
                    >
                      Edit payload
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card>
        <CardHeader title="Failed sync logs" description="Outbound integration failures (QBO, Xero, Shopify, EasyPost)" />
        {syncLoading ? (
          <TableSkeleton rows={4} cols={4} />
        ) : syncLogs.length === 0 ? (
          <p className="text-sm text-text-muted">No failed sync logs.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Error</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {syncLogs.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="font-mono text-sm">{log.system}</TableCell>
                  <TableCell className="text-sm">{log.entityType}</TableCell>
                  <TableCell className="max-w-xs truncate text-sm text-danger">{log.lastError ?? '—'}</TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="secondary" onClick={() => retrySync.mutate(log.id)}>
                      Retry
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <AuditLogTable />
      <HistoricalArchivesPanel />

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title="Edit outbox payload"
        description={editing?.eventType}
      >
        <textarea
          className="min-h-48 w-full rounded-md border border-border bg-surface-raised p-3 font-mono text-xs text-text"
          value={payloadText}
          onChange={(e) => setPayloadText(e.target.value)}
          aria-label="Outbox payload JSON"
        />
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setEditing(null)}>
            Cancel
          </Button>
          <Button loading={savePayload.isPending} onClick={() => savePayload.mutate()}>
            Save &amp; retry
          </Button>
        </div>
      </Modal>
    </div>
  );
}

/* ------------------------------------- Page ------------------------------------- */

function CostCentersRequisitionsTab() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [budget, setBudget] = useState('');
  const [costCenterId, setCostCenterId] = useState('');
  const [variantId, setVariantId] = useState('');
  const [qty, setQty] = useState('1');

  const { data: costCenters = [], isLoading: loadingCc } = useQuery({
    queryKey: ['cost-centers'],
    queryFn: async () => (await apiClient.get<CostCenter[]>('/api/v1/cost-centers')).data,
    retry: false,
  });

  const { data: requisitions = [], isLoading: loadingReq } = useQuery({
    queryKey: ['internal-requisitions', 'settings'],
    queryFn: async () =>
      (await apiClient.get<InternalRequisition[]>('/api/v1/internal-requisitions')).data,
    retry: false,
  });

  const createCc = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/cost-centers', {
        code,
        name,
        budget: budget ? Number(budget) : null,
      });
    },
    onSuccess: () => {
      setCode('');
      setName('');
      setBudget('');
      void queryClient.invalidateQueries({ queryKey: ['cost-centers'] });
    },
  });

  const createReq = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/internal-requisitions', {
        costCenterId,
        lines: [{ variantId, qtyRequested: Number(qty) }],
      });
    },
    onSuccess: () => {
      setVariantId('');
      setQty('1');
      void queryClient.invalidateQueries({ queryKey: ['internal-requisitions'] });
    },
  });

  const approveReq = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/internal-requisitions/${id}/approve`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['internal-requisitions'] });
    },
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title="Cost centers" description="Departments that consume internal stock" />
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <Input placeholder="Code" value={code} onChange={(e) => setCode(e.target.value)} />
          <Input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <Input
            placeholder="Budget"
            type="number"
            value={budget}
            onChange={(e) => setBudget(e.target.value)}
          />
        </div>
        <Button
          size="sm"
          disabled={!code.trim() || !name.trim() || createCc.isPending}
          onClick={() => createCc.mutate()}
        >
          Add cost center
        </Button>
        <div className="mt-4">
          {loadingCc ? (
            <TableSkeleton rows={3} cols={3} />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Budget</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {costCenters.map((cc) => (
                  <TableRow key={cc.id}>
                    <TableCell mono>{cc.code}</TableCell>
                    <TableCell>{cc.name}</TableCell>
                    <TableCell>{cc.budget ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Internal requisitions"
          description="Create and approve stockroom requisitions"
        />
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <Select
            value={costCenterId}
            onChange={(e) => setCostCenterId(e.target.value)}
            aria-label="Cost center"
          >
            <option value="">Select cost center</option>
            {costCenters.map((cc) => (
              <option key={cc.id} value={cc.id}>
                {cc.code} — {cc.name}
              </option>
            ))}
          </Select>
          <Input
            placeholder="Variant ID"
            value={variantId}
            onChange={(e) => setVariantId(e.target.value)}
          />
          <Input
            type="number"
            min={1}
            placeholder="Qty"
            value={qty}
            onChange={(e) => setQty(e.target.value)}
          />
        </div>
        <Button
          size="sm"
          disabled={!costCenterId || !variantId || createReq.isPending}
          onClick={() => createReq.mutate()}
        >
          Create requisition
        </Button>
        <div className="mt-4">
          {loadingReq ? (
            <TableSkeleton rows={4} cols={4} />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Number</TableHead>
                  <TableHead>Cost center</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead align="right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {requisitions.map((req) => (
                  <TableRow key={req.id}>
                    <TableCell mono>{req.requisitionNumber}</TableCell>
                    <TableCell>{req.costCenterCode ?? req.costCenterId}</TableCell>
                    <TableCell>{req.status}</TableCell>
                    <TableCell>
                      {req.status === 'DRAFT' && (
                        <Button size="sm" variant="secondary" onClick={() => approveReq.mutate(req.id)}>
                          Approve
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
      </Card>
    </div>
  );
}

export function SettingsPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const isOwner = useSessionStore((s) => s.user?.roles?.includes('OWNER') ?? false);
  const sessionUser = useSessionStore((s) => s.user);
  const { hasModule } = useEntitlement();
  const showRetailPos = canConfigureRetailPos(sessionUser?.roles, sessionUser?.enabledModules);
  const visibleTabs = useMemo(
    () =>
      TABS.filter((tab) => {
        if (tab.id === 'retailPos' && !showRetailPos) return false;
        if ('requiredModule' in tab && tab.requiredModule && !hasModule(tab.requiredModule)) return false;
        return true;
      }),
    [hasModule, showRetailPos],
  );
  const visibleSubroutes = useMemo(
    () =>
      SETTINGS_SUBROUTES.filter((link) => {
        if (link.ownerOnly && !isOwner) return false;
        if (link.requiredModule && !hasModule(link.requiredModule)) return false;
        if (link.anyOfModules && !link.anyOfModules.some((moduleName) => hasModule(moduleName))) {
          return false;
        }
        return true;
      }),
    [hasModule, isOwner],
  );
  const tabParam = searchParams.get('tab');
  const initialTab = visibleTabs.some((tab) => tab.id === tabParam) ? (tabParam as TabId) : 'profile';
  const [activeTab, setActiveTab] = useState<TabId>(initialTab);

  useEffect(() => {
    if (tabParam && !visibleTabs.some((tab) => tab.id === tabParam)) {
      setActiveTab('profile');
      return;
    }
    if (visibleTabs.some((tab) => tab.id === tabParam)) {
      setActiveTab(tabParam as TabId);
    }
  }, [tabParam, visibleTabs]);

  const selectTab = (tab: TabId) => {
    setActiveTab(tab);
    // Keep URL in sync so Page Info / support copilot resolve tab-specific playbooks.
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('tab', tab);
        return next;
      },
      { replace: true },
    );
  };

  return (
    <div
      className="flex h-full min-h-0 flex-col overflow-hidden"
      data-testid="settings-page"
    >
      <div className="settings-shell flex min-h-0 flex-1 flex-col px-4 pt-6 sm:px-6 lg:px-8">
        <div className="mb-4 shrink-0 sm:mb-6">
          <h1 className="text-2xl font-bold text-text text-wrap-balance">{t('settings.title')}</h1>
          <p className="mt-1 max-w-2xl text-sm text-text-muted">{t('settings.subtitle')}</p>
        </div>

        <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 overflow-hidden lg:grid-cols-12 lg:gap-6">
          {/* Left nav: rail-style — scrollbar hidden; fade/chevrons when more items exist. */}
          <ScrollFadePort
            as="nav"
            aria-label={t('settings.sections')}
            data-testid="settings-nav"
            measureKey={activeTab}
            shellClassName="settings-shell__nav shrink-0 lg:col-span-3 lg:h-full xl:col-span-2"
            className="flex h-full gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-x-hidden lg:overflow-y-auto lg:pb-6"
          >
            {visibleTabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => selectTab(tab.id)}
                data-testid={`settings-tab-${tab.id}`}
                aria-current={activeTab === tab.id ? 'page' : undefined}
                className={cn(
                  'shrink-0 whitespace-nowrap rounded-md px-3 py-2 text-left text-sm font-medium transition-colors lg:w-full lg:whitespace-normal',
                  activeTab === tab.id
                    ? 'bg-accent-muted text-accent'
                    : 'text-text-muted hover:bg-surface-overlay hover:text-text',
                )}
              >
                {t(tab.labelKey)}
              </button>
            ))}
            <div className="my-1 hidden border-t border-border lg:block" aria-hidden />
            {visibleSubroutes.map(
              (link) => (
                <Link
                  key={link.to}
                  to={link.to}
                  data-testid={
                    link.to === '/settings/integrations' ? 'settings-nav-integrations-hub' : undefined
                  }
                  className={cn(
                    'shrink-0 whitespace-nowrap rounded-md px-3 py-2 text-left text-sm font-medium transition-colors lg:w-full lg:whitespace-normal',
                    link.to === '/settings/integrations'
                      ? 'bg-accent-muted/60 font-semibold text-accent hover:bg-accent-muted'
                      : 'text-text-muted hover:bg-surface-overlay hover:text-text',
                  )}
                >
                  {t(link.labelKey)}
                </Link>
              ),
            )}
          </ScrollFadePort>

          {/* Right panel: sole content scrollport; sticky heads stay inside this port. */}
          <ScrollFadePort
            data-testid="settings-content"
            data-settings-scrollport="true"
            data-list-scrollport="true"
            measureKey={activeTab}
            shellClassName="min-h-0 min-w-0 lg:col-span-9 xl:col-span-10"
            className="h-full overflow-y-auto overflow-x-hidden pb-6"
          >
            {activeTab === 'profile' && <ProfileTab />}
            {activeTab === 'users' && <UsersTab />}
            {activeTab === 'warehouses' && <WarehousesTab />}
            {activeTab === 'inventory' && <InventoryRulesTab />}
            {activeTab === 'documents' && (
              <RequireModule required="DOCUMENTS">
                <DocumentsTab />
              </RequireModule>
            )}
            {activeTab === 'retailPos' && (
              <RequireModule required="RETAIL_POS">
                {showRetailPos ? <PosSettingsPanel /> : null}
              </RequireModule>
            )}
            {activeTab === 'security' && <SecuritySsoTab />}
            {activeTab === 'reconciliation' && <ReconciliationTab />}
            {activeTab === 'accounting' && (
              <RequireModule required="ACCOUNTING">
                <AccountingSync />
              </RequireModule>
            )}
            {activeTab === 'integrations' && (
              <RequireModule required="SHOPIFY">
                <Integrations />
              </RequireModule>
            )}
            {activeTab === 'mesh' && (
              <RequireModule required="MESH_NETWORK">
                <PartnerCatalogMappingPanel />
              </RequireModule>
            )}
            {activeTab === 'operations' && <OperationsConsoleTab />}
            {activeTab === 'automations' && <AutomationSettings />}
            {activeTab === 'syncConflicts' && <SyncConflictsPanel />}
            {activeTab === 'costCenters' && <CostCentersRequisitionsTab />}
          </ScrollFadePort>
        </div>
      </div>
    </div>
  );
}
