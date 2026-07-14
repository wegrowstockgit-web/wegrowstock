import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { Link2, Plus, RefreshCw, Trash2, UserPlus, Warehouse as WarehouseIcon } from 'lucide-react';
import { apiClient } from '@/api/client';
import type {
  AccountMapping,
  ChannelIntegration,
  CostCenter,
  Customer,
  InternalRequisition,
  OutboxEventItem,
  PlatformAlertItem,
  AuditLogItem,
  ShippingCredentialStatus,
  SsoConfig,
  StripeBillingStatus,
  SyncLog,
  TaxRate,
  TenantEmailDomain,
  TenantLocation,
  TenantSettingsMap,
  TenantUser,
  UpdateAccountMapping,
} from '@/api/types';
import { cn } from '@/lib/utils';
import { useSessionStore } from '@/stores/session';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { FinancingCockpit } from '@/components/fintech/FinancingCockpit';
import { useToast } from '@/components/ui/Toast';

const TABS = [
  { id: 'profile', label: 'Profile' },
  { id: 'users', label: 'Users' },
  { id: 'warehouses', label: 'Warehouses' },
  { id: 'inventory', label: 'Inventory Rules' },
  { id: 'documents', label: 'Documents' },
  { id: 'billing', label: 'Billing' },
  { id: 'financing', label: 'Cash Flow & Financing' },
  { id: 'security', label: 'Security & SSO' },
  { id: 'reconciliation', label: 'Reconciliation' },
  { id: 'accounting', label: 'Accounting Sync' },
  { id: 'integrations', label: 'Integrations' },
  { id: 'operations', label: 'Operations' },
  { id: 'costCenters', label: 'Cost Centers & Requisitions' },
] as const;

type TabId = (typeof TABS)[number]['id'];

const ASSIGNABLE_ROLES = ['ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER', 'B2B_CUSTOMER'] as const;

const ACCOUNT_TYPES = ['INVENTORY_ASSET', 'COGS', 'SALES_REVENUE', 'TAX'] as const;

const SYSTEMS = ['QUICKBOOKS', 'XERO'] as const;

const SYNC_STATUS_STYLES: Record<string, string> = {
  SYNCED: 'bg-success/20 text-success',
  PENDING: 'bg-warning/20 text-warning',
  FAILED: 'bg-danger/20 text-danger',
  SKIPPED: 'bg-surface-overlay text-text-muted',
  ACTIVE: 'bg-success/20 text-success',
  DISCONNECTED: 'bg-surface-overlay text-text-muted',
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
  if (!show) return null;
  return <span className="text-sm text-success">Saved</span>;
}

/* ------------------------------------ Profile ----------------------------------- */

function ProfileTab() {
  const user = useSessionStore((s) => s.user);
  const setAvatarUrl = useSessionStore((s) => s.setAvatarUrl);
  const settings = useTenantSettings();
  const [currency, setCurrency] = useState('');

  useEffect(() => {
    if (settings.data && typeof settings.data.currency === 'string') {
      setCurrency(settings.data.currency);
    }
  }, [settings.data]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader title="Your account" description="Signed-in user details" />
        <div className="space-y-4">
          <div data-testid="profile-avatar-picker">
            <p className="mb-2 text-sm font-medium text-text">Profile photo</p>
            <MediaPicker
              kind="AVATAR"
              label="Upload photo"
              capture
              previewUrl={user?.avatarUrl}
              onUploaded={async (result) => {
                if (result.contentUrl) setAvatarUrl(result.contentUrl);
              }}
            />
          </div>
          <Input label="Display name" value={user?.displayName ?? ''} disabled />
          <Input label="Email" value={user?.email ?? ''} disabled />
          <Input label="Roles" value={(user?.roles ?? []).join(', ')} disabled />
        </div>
      </Card>

      <Card>
        <CardHeader title="Company preferences" description="Applied across the whole workspace" />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            settings.patch.mutate({ currency });
          }}
          className="space-y-4"
        >
          <Select label="Base currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            {['USD', 'EUR', 'GBP', 'CAD', 'AUD'].map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
          <div className="flex items-center gap-3">
            <Button type="submit" loading={settings.patch.isPending}>
              Save changes
            </Button>
            <SavedNote show={settings.patch.isSuccess && !settings.patch.isPending} />
          </div>
        </form>
      </Card>
    </div>
  );
}

/* ------------------------------------- Users ------------------------------------ */

function InviteUserModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<string>('VIEWER');
  const [customerId, setCustomerId] = useState('');
  const [error, setError] = useState('');

  const { data: customers = [] } = useQuery({
    queryKey: ['customers'],
    queryFn: async () => (await apiClient.get<Customer[]>('/api/v1/customers')).data,
    enabled: open && role === 'B2B_CUSTOMER',
  });

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/users/invitations', {
        email,
        role,
        customerId: role === 'B2B_CUSTOMER' ? customerId : undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      setEmail('');
      setRole('VIEWER');
      setCustomerId('');
      onClose();
    },
    onError: () => setError('Could not send the invitation. The user may already exist.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Invite user" description="They will receive a link to join this workspace">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoFocus
        />
        <Select label="Role" value={role} onChange={(e) => setRole(e.target.value)}>
          {ASSIGNABLE_ROLES.map((r) => (
            <option key={r} value={r}>
              {r.replaceAll('_', ' ')}
            </option>
          ))}
        </Select>
        {role === 'B2B_CUSTOMER' && (
          <Select
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
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Send invitation
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function UsersTab() {
  const queryClient = useQueryClient();
  const currentUser = useSessionStore((s) => s.user);
  const [inviteOpen, setInviteOpen] = useState(false);

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: async () => (await apiClient.get<TenantUser[]>('/api/v1/users')).data,
    retry: false,
  });

  const roleMutation = useMutation({
    mutationFn: async ({ id, role }: { id: string; role: string }) => {
      await apiClient.patch(`/api/v1/users/${id}/role`, { role });
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  const deactivateMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/users/${id}/deactivate`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  return (
    <Card>
      <CardHeader
        title="Users & invitations"
        description="Invite team members and manage roles"
        action={
          <Button size="sm" onClick={() => setInviteOpen(true)}>
            <UserPlus className="h-4 w-4" />
            Invite user
          </Button>
        }
      />
      {isLoading ? (
        <TableSkeleton rows={5} cols={4} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>User</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead align="right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((u) => {
              const isSelf = u.id === currentUser?.id;
              const isOwner = u.roles.includes('OWNER');
              return (
                <TableRow key={u.id}>
                  <TableCell>
                    <p className="font-medium text-text">{u.displayName}</p>
                    <p className="text-xs text-text-muted">{u.email}</p>
                  </TableCell>
                  <TableCell>
                    {isOwner || isSelf ? (
                      <span className="text-sm">{u.roles.join(', ') || '—'}</span>
                    ) : (
                      <Select
                        aria-label={`Role for ${u.email}`}
                        value={u.roles[0] ?? 'VIEWER'}
                        disabled={roleMutation.isPending}
                        onChange={(e) => roleMutation.mutate({ id: u.id, role: e.target.value })}
                        className="h-8 w-44 text-xs"
                      >
                        {ASSIGNABLE_ROLES.filter((r) => r !== 'B2B_CUSTOMER').map((r) => (
                          <option key={r} value={r}>
                            {r.replaceAll('_', ' ')}
                          </option>
                        ))}
                      </Select>
                    )}
                  </TableCell>
                  <TableCell>{statusChip(u.status)}</TableCell>
                  <TableCell align="right">
                    {!isSelf && !isOwner && u.status === 'ACTIVE' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={deactivateMutation.isPending}
                        onClick={() => deactivateMutation.mutate(u.id)}
                      >
                        Deactivate
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
      <InviteUserModal open={inviteOpen} onClose={() => setInviteOpen(false)} />
    </Card>
  );
}

/* ---------------------------------- Warehouses ---------------------------------- */

function AddWarehouseModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/locations', {
        type: 'WAREHOUSE',
        code,
        name,
        path: code,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locations'] });
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      setName('');
      setCode('');
      onClose();
    },
    onError: () => setError('Could not create the warehouse. Check the fields and try again.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add warehouse" description="Top-level location; add zones and bins beneath it">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Input
          label="Code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="e.g. WH2"
          required
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Add warehouse
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function WarehousesTab() {
  const [modalOpen, setModalOpen] = useState(false);
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
          title="Warehouses & locations"
          description="Warehouse hierarchy: warehouses, zones, aisles, and bins"
          action={
            <Button size="sm" onClick={() => setModalOpen(true)}>
              <WarehouseIcon className="h-4 w-4" />
              Add warehouse
            </Button>
          }
        />
        {isLoading ? (
          <TableSkeleton rows={6} cols={3} />
        ) : locations.length === 0 ? (
          <p className="text-sm text-text-muted">No locations yet. Add your first warehouse to get started.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Path</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Photo</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {locations.map((loc) => (
                <TableRow key={loc.id}>
                  <TableCell mono>{loc.path}</TableCell>
                  <TableCell>{loc.name}</TableCell>
                  <TableCell>
                    <span className="rounded-full bg-surface-overlay px-2 py-0.5 text-xs font-medium text-text-muted">
                      {loc.type}
                    </span>
                  </TableCell>
                  <TableCell>
                    <MediaPicker
                      kind="LOCATION"
                      label="Photo"
                      capture
                      className="min-w-[12rem]"
                      onUploaded={async (result) => {
                        await apiClient.post('/api/v1/media/attachments', {
                          mediaObjectId: result.id,
                          entityType: 'LOCATION',
                          entityId: loc.id,
                          purpose: 'LOCATION',
                        });
                      }}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <AddWarehouseModal open={modalOpen} onClose={() => setModalOpen(false)} />
      </Card>

      <Card>
        <CardHeader
          title="Terminal context gate"
          description="Auto-assign warehouse from Wi-Fi SSID or GPS geofence — hides the switcher when matched"
        />
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <label className="text-sm">
            <span className="mb-1 block text-text-muted">Wi-Fi SSID</span>
            <input
              className="h-9 w-full rounded-md border border-border bg-surface-raised px-2 text-sm"
              value={ssid}
              onChange={(e) => setSsid(e.target.value)}
              aria-label="Wi-Fi SSID"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-text-muted">Warehouse</span>
            <select
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
  const [taxName, setTaxName] = useState('');
  const [taxRate, setTaxRate] = useState('');

  const { data: taxRates = [] } = useQuery({
    queryKey: ['tax-rates'],
    queryFn: async () => (await apiClient.get<TaxRate[]>('/api/v1/settings/taxes')).data,
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
            });
          }}
          className="space-y-4"
        >
          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              checked={allowNegative}
              onChange={(e) => setAllowNegative(e.target.checked)}
              className="h-4 w-4 rounded border-border accent-accent"
            />
            <span className="text-sm text-text">Allow negative inventory</span>
          </label>
          <label className="flex items-center gap-3">
            <input
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
          <div className="flex items-center gap-3">
            <Button type="submit" loading={settings.patch.isPending}>
              Save rules
            </Button>
            <SavedNote show={settings.patch.isSuccess && !settings.patch.isPending} />
          </div>
        </form>
      </Card>

      <Card>
        <CardHeader title="Tax configuration" description="Default tax applied to new sales order lines" />
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

  useEffect(() => {
    const s = settings.data;
    if (!s) return;
    if (typeof s.invoice_number_format === 'string') setInvoiceFormat(s.invoice_number_format);
    if (typeof s.sales_order_number_format === 'string') setSoFormat(s.sales_order_number_format);
    if (typeof s.purchase_order_number_format === 'string') setPoFormat(s.purchase_order_number_format);
  }, [settings.data]);

  return (
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
  );
}

/* ------------------------------------ Financing ----------------------------------- */

function FinancingTab() {
  return <FinancingCockpit />;
}

/* ------------------------------------ Billing ----------------------------------- */

function BillingTab() {
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const [error, setError] = useState('');
  const [shippingSystem, setShippingSystem] = useState('EASYPOST');
  const [shippingKey, setShippingKey] = useState('');
  const [domainName, setDomainName] = useState('');

  const { data: stripeStatus, refetch: refetchStripe } = useQuery({
    queryKey: ['billing', 'stripe-status'],
    queryFn: async () =>
      (await apiClient.get<StripeBillingStatus>('/api/v1/billing/stripe/status')).data,
    retry: false,
  });

  const { data: shippingAccounts = [] } = useQuery({
    queryKey: ['shipping-accounts'],
    queryFn: async () =>
      (await apiClient.get<ShippingCredentialStatus[]>('/api/v1/settings/shipping-accounts')).data,
    retry: false,
  });

  const { data: emailDomains = [] } = useQuery({
    queryKey: ['email-domains'],
    queryFn: async () =>
      (await apiClient.get<TenantEmailDomain[]>('/api/v1/settings/email-domains')).data,
    retry: false,
  });

  useEffect(() => {
    if (searchParams.get('stripe') === 'success') {
      void apiClient.get('/api/v1/billing/stripe/refresh').then(() => {
        void refetchStripe();
        void queryClient.invalidateQueries({ queryKey: ['billing', 'stripe-status'] });
      });
    }
  }, [searchParams, refetchStripe, queryClient]);

  const connectMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.get<{ url: string }>(
        '/api/v1/billing/stripe/onboarding-url',
        { params: { returnUrl: `${window.location.origin}/settings?stripe=success` } }
      );
      return res.data.url;
    },
    onSuccess: (url) => {
      window.location.href = url;
    },
    onError: () => setError('Could not start Stripe onboarding. Try again.'),
  });

  const saveShippingMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/shipping-accounts', {
        system: shippingSystem,
        apiKey: shippingKey,
      });
    },
    onSuccess: () => {
      setShippingKey('');
      void queryClient.invalidateQueries({ queryKey: ['shipping-accounts'] });
    },
  });

  const registerDomainMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/email-domains', { domainName });
    },
    onSuccess: () => {
      setDomainName('');
      void queryClient.invalidateQueries({ queryKey: ['email-domains'] });
    },
  });

  const verifyDomainMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/settings/email-domains/${id}/verify`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['email-domains'] }),
  });

  return (
    <div className="space-y-6">
      <FinancingCockpit />

      <Card>
        <CardHeader title="Billing & payments" description="Stripe Connect and platform fees" />
        <div className="mb-4 flex flex-wrap gap-2">
          {statusChip(stripeStatus?.onboardingStatus ?? 'NOT_CONNECTED')}
          {stripeStatus?.connectedAccountId && (
            <span className="font-mono text-xs text-text-muted">{stripeStatus.connectedAccountId}</span>
          )}
        </div>
        {stripeStatus?.capabilities && Object.keys(stripeStatus.capabilities).length > 0 && (
          <div className="mb-4 flex flex-wrap gap-2">
            {Object.entries(stripeStatus.capabilities).map(([key, value]) => (
              <span key={key} className="text-xs text-text-muted">
                {key}: {String(value)}
              </span>
            ))}
          </div>
        )}
        <p className="text-sm text-text-muted">
          Connect your Stripe account to receive invoice payments directly. Platform fees apply per
          your tenant settings.
        </p>
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
        <Button
          className="mt-4"
          loading={connectMutation.isPending}
          onClick={() => {
            setError('');
            connectMutation.mutate();
          }}
        >
          <Link2 className="h-4 w-4" />
          Connect Stripe
        </Button>
      </Card>

      <Card>
        <CardHeader title="Shipping accounts" description="Carrier credentials for label generation" />
        <form
          className="mb-4 grid gap-4 sm:grid-cols-3"
          onSubmit={(e) => {
            e.preventDefault();
            saveShippingMutation.mutate();
          }}
        >
          <Select label="Carrier" value={shippingSystem} onChange={(e) => setShippingSystem(e.target.value)}>
            {['EASYPOST', 'UPS', 'FEDEX'].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </Select>
          <Input
            label="API key"
            type="password"
            value={shippingKey}
            onChange={(e) => setShippingKey(e.target.value)}
            required
          />
          <div className="flex items-end">
            <Button type="submit" loading={saveShippingMutation.isPending}>
              Save credential
            </Button>
          </div>
        </form>
        <div className="flex flex-wrap gap-2">
          {shippingAccounts.map((account) => (
            <span key={account.system}>
              {account.system}: {statusChip(account.status)}
            </span>
          ))}
        </div>
      </Card>

      <Card>
        <CardHeader title="Email domains" description="Custom domains for PO and invoice emails" />
        <form
          className="mb-4 flex flex-wrap gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            registerDomainMutation.mutate();
          }}
        >
          <Input
            label="Domain"
            value={domainName}
            onChange={(e) => setDomainName(e.target.value)}
            placeholder="mail.yourcompany.com"
            required
          />
          <div className="flex items-end">
            <Button type="submit" loading={registerDomainMutation.isPending}>
              Add domain
            </Button>
          </div>
        </form>
        {emailDomains.length === 0 ? (
          <p className="text-sm text-text-muted">No custom domains registered.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Domain</TableHead>
                <TableHead>Status</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {emailDomains.map((domain) => (
                <TableRow key={domain.id}>
                  <TableCell>{domain.domainName}</TableCell>
                  <TableCell>{statusChip(domain.verificationStatus)}</TableCell>
                  <TableCell align="right">
                    {domain.verificationStatus === 'PENDING' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => verifyDomainMutation.mutate(domain.id)}
                        loading={verifyDomainMutation.isPending}
                      >
                        Verify
                      </Button>
                    )}
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

/* ------------------------------------ Security & SSO ----------------------------------- */

function SecuritySsoTab() {
  const queryClient = useQueryClient();
  const [issuerUrl, setIssuerUrl] = useState('');
  const [clientId, setClientId] = useState('');
  const [clientSecret, setClientSecret] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [forceSso, setForceSso] = useState(false);
  const [protocol, setProtocol] = useState<'OIDC' | 'SAML'>('OIDC');
  const [samlMetadataUrl, setSamlMetadataUrl] = useState('');
  const [samlEntityId, setSamlEntityId] = useState('');
  const [error, setError] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'sso'],
    queryFn: async () => (await apiClient.get<SsoConfig>('/api/v1/settings/sso')).data,
    retry: false,
  });

  useEffect(() => {
    if (!data) return;
    setIssuerUrl(data.issuerUrl);
    setClientId(data.clientId);
    setEnabled(data.enabled);
    setForceSso(data.forceSso);
    setProtocol(data.protocol === 'SAML' ? 'SAML' : 'OIDC');
    setSamlMetadataUrl(data.samlMetadataUrl ?? '');
    setSamlEntityId(data.samlEntityId ?? '');
  }, [data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.put('/api/v1/settings/sso', {
        issuerUrl,
        clientId,
        clientSecret: clientSecret || undefined,
        enabled,
        forceSso,
        protocol,
        samlMetadataUrl: samlMetadataUrl || undefined,
        samlEntityId: samlEntityId || undefined,
      });
    },
    onSuccess: () => {
      setClientSecret('');
      setError('');
      void queryClient.invalidateQueries({ queryKey: ['settings', 'sso'] });
    },
    onError: () => setError('Could not save SSO settings. Check issuer URL, client ID, and secret.'),
  });

  if (isLoading) return <TableSkeleton rows={4} cols={2} />;

  return (
    <Card>
      <CardHeader
        title="Security & SSO"
        description="OIDC or SAML routing for Okta, Azure AD / Entra ID"
      />
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          saveMutation.mutate();
        }}
        className="space-y-4"
      >
        <label className="block space-y-1.5">
          <span className="text-sm font-medium text-text">Protocol</span>
          <select
            value={protocol}
            onChange={(e) => setProtocol(e.target.value === 'SAML' ? 'SAML' : 'OIDC')}
            className="h-10 w-full rounded-md border border-border bg-surface-raised px-3 text-sm"
          >
            <option value="OIDC">OIDC (OAuth2)</option>
            <option value="SAML">SAML 2.0</option>
          </select>
        </label>
        <Input
          label="Issuer URL"
          value={issuerUrl}
          onChange={(e) => setIssuerUrl(e.target.value)}
          placeholder="https://your-org.okta.com/oauth2/default"
          required={protocol === 'OIDC'}
        />
        {protocol === 'SAML' && (
          <>
            <Input
              label="SAML metadata URL"
              value={samlMetadataUrl}
              onChange={(e) => setSamlMetadataUrl(e.target.value)}
              placeholder="https://login.microsoftonline.com/.../federationmetadata/..."
            />
            <Input
              label="SAML entity ID"
              value={samlEntityId}
              onChange={(e) => setSamlEntityId(e.target.value)}
            />
          </>
        )}
        <Input
          label="Client ID"
          value={clientId}
          onChange={(e) => setClientId(e.target.value)}
          required={protocol === 'OIDC'}
        />
        <Input
          label="Client secret"
          type="password"
          value={clientSecret}
          onChange={(e) => setClientSecret(e.target.value)}
          placeholder={data?.configured ? 'Leave blank to keep existing secret' : 'Required on first save'}
          required={!data?.configured}
        />
        <label className="flex items-center gap-3">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="h-4 w-4 rounded border-border accent-accent"
          />
          <span className="text-sm text-text">Enable SSO for this workspace</span>
        </label>
        <label className="flex items-center gap-3">
          <input
            type="checkbox"
            checked={forceSso}
            disabled={!enabled}
            onChange={(e) => setForceSso(e.target.checked)}
            className="h-4 w-4 rounded border-border accent-accent disabled:opacity-50"
          />
          <span className="text-sm text-text">
            Force corporate SSO (block password login)
          </span>
        </label>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex items-center gap-3">
          <Button type="submit" loading={saveMutation.isPending}>
            Save SSO settings
          </Button>
          <SavedNote show={saveMutation.isSuccess && !saveMutation.isPending} />
        </div>
      </form>
    </Card>
  );
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
    <div className="space-y-6">
      <Card>
        <CardHeader title="Financial truth" description="Physical inventory vs accounting sync" />
        <div className="grid gap-4 sm:grid-cols-3">
          <div className="rounded-lg border border-border p-4">
            <p className="text-xs text-text-muted">Physical inventory value</p>
            <p className="mt-1 font-mono text-xl font-semibold text-text">
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

/* ------------------------------- Accounting sync -------------------------------- */

function AccountingSyncTab() {
  const queryClient = useQueryClient();
  const [draftMappings, setDraftMappings] = useState<UpdateAccountMapping[]>([]);

  const { data: mappings = [], isLoading: mappingsLoading } = useQuery({
    queryKey: ['integrations', 'accounting', 'mappings'],
    queryFn: async () => {
      const res = await apiClient.get<AccountMapping[]>(
        '/api/v1/integrations/accounting/mappings'
      );
      return res.data;
    },
    retry: false,
  });

  const { data: syncLogs = [], isLoading: logsLoading, refetch: refetchLogs } = useQuery({
    queryKey: ['integrations', 'sync-logs'],
    queryFn: async () => {
      const res = await apiClient.get<SyncLog[]>('/api/v1/integrations/sync-logs');
      return res.data;
    },
    retry: false,
  });

  useEffect(() => {
    if (mappings.length > 0) {
      setDraftMappings(
        mappings.map((m) => ({
          system: m.system,
          accountType: m.accountType,
          externalAccountId: m.externalAccountId,
        }))
      );
    }
  }, [mappings]);

  const saveMutation = useMutation({
    mutationFn: async (payload: UpdateAccountMapping[]) => {
      await apiClient.put('/api/v1/integrations/accounting/mappings/bulk', { mappings: payload });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['integrations', 'accounting', 'mappings'] });
    },
  });

  const retryMutation = useMutation({
    mutationFn: async (logId: string) => {
      await apiClient.post(`/api/v1/integrations/sync-logs/${logId}/retry`);
    },
    onSuccess: () => {
      void refetchLogs();
    },
  });

  const updateMapping = (
    system: string,
    accountType: string,
    externalAccountId: string
  ) => {
    setDraftMappings((prev) => {
      const existing = prev.find(
        (m) => m.system === system && m.accountType === accountType
      );
      if (existing) {
        return prev.map((m) =>
          m.system === system && m.accountType === accountType
            ? { ...m, externalAccountId }
            : m
        );
      }
      return [...prev, { system, accountType, externalAccountId }];
    });
  };

  const getMappingValue = (system: string, accountType: string) => {
    const fromDraft = draftMappings.find(
      (m) => m.system === system && m.accountType === accountType
    );
    if (fromDraft) return fromDraft.externalAccountId;
    return (
      mappings.find((m) => m.system === system && m.accountType === accountType)
        ?.externalAccountId ?? ''
    );
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Accounting connections"
          description="QuickBooks and Xero sync uses the account mappings below; OAuth connection is managed by support"
        />
        <div className="flex flex-wrap gap-3">
          {SYSTEMS.map((system) => (
            <Button
              key={system}
              variant="secondary"
              disabled
              title="OAuth onboarding is not available yet — configure mappings below"
            >
              <Link2 className="h-4 w-4" />
              Connect {system === 'QUICKBOOKS' ? 'QuickBooks' : 'Xero'}
            </Button>
          ))}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Account mappings"
          description="Map inventory accounts to your chart of accounts"
          action={
            <Button
              size="sm"
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate(draftMappings)}
            >
              Save mappings
            </Button>
          }
        />
        {mappingsLoading ? (
          <TableSkeleton rows={4} cols={3} />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Account type</TableHead>
                <TableHead>External account ID</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {SYSTEMS.flatMap((system) =>
                ACCOUNT_TYPES.map((accountType) => (
                  <TableRow key={`${system}-${accountType}`}>
                    <TableCell>{system}</TableCell>
                    <TableCell>{accountType}</TableCell>
                    <TableCell>
                      <Input
                        value={getMappingValue(system, accountType)}
                        onChange={(e) =>
                          updateMapping(system, accountType, e.target.value)
                        }
                        placeholder="External account ID"
                      />
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card>
        <CardHeader
          title="Sync log"
          description="Recent integration sync attempts"
          action={
            <Button variant="ghost" size="sm" onClick={() => refetchLogs()}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
          }
        />
        {logsLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : syncLogs.length === 0 ? (
          <p className="text-sm text-text-muted">No sync activity yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Retries</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {syncLogs.map((log) => (
                <TableRow key={log.id}>
                  <TableCell>{log.system}</TableCell>
                  <TableCell>
                    <span className="text-text-muted">{log.entityType}</span>
                    <span className="ml-1 font-mono text-xs">{log.entityId.slice(0, 8)}</span>
                  </TableCell>
                  <TableCell>{statusChip(log.status)}</TableCell>
                  <TableCell mono>{log.retryCount}</TableCell>
                  <TableCell>
                    {log.status === 'FAILED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={retryMutation.isPending}
                        onClick={() => retryMutation.mutate(log.id)}
                      >
                        Retry
                      </Button>
                    )}
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

/* ---------------------------------- Integrations -------------------------------- */

function ConnectChannelModal({
  platform,
  onClose,
}: {
  platform: string | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [shopIdentifier, setShopIdentifier] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/integrations/channels', {
        platform,
        shopIdentifier,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] });
      setShopIdentifier('');
      onClose();
    },
    onError: () => setError('Could not connect. That shop may already be connected.'),
  });

  return (
    <Modal
      open={platform !== null}
      onClose={onClose}
      title={`Connect ${platform === 'SHOPIFY' ? 'Shopify' : 'Amazon'}`}
      description={
        platform === 'SHOPIFY'
          ? 'Enter your shop domain, e.g. my-store.myshopify.com'
          : 'Enter your Amazon seller ID'
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input
          label={platform === 'SHOPIFY' ? 'Shop domain' : 'Seller ID'}
          value={shopIdentifier}
          onChange={(e) => setShopIdentifier(e.target.value)}
          required
          autoFocus
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Connect
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function IntegrationsTab() {
  const queryClient = useQueryClient();
  const [connectPlatform, setConnectPlatform] = useState<string | null>(null);

  const { data: channels = [], isLoading, refetch } = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: async () => {
      const res = await apiClient.get<ChannelIntegration[]>(
        '/api/v1/integrations/channels'
      );
      return res.data;
    },
    retry: false,
  });

  const disconnectMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/api/v1/integrations/channels/${id}`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] }),
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title="Channel integrations"
          description="Connect Shopify and Amazon for multi-channel sync"
        />
        <div className="flex flex-wrap gap-3">
          <Button variant="secondary" onClick={() => setConnectPlatform('SHOPIFY')}>
            <Plus className="h-4 w-4" />
            Connect Shopify
          </Button>
          <Button variant="secondary" onClick={() => setConnectPlatform('AMAZON')}>
            <Plus className="h-4 w-4" />
            Connect Amazon
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Connected channels"
          description="Live health and sync status"
          action={
            <Button variant="ghost" size="sm" onClick={() => refetch()}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
          }
        />
        {isLoading ? (
          <TableSkeleton rows={4} cols={5} />
        ) : channels.length === 0 ? (
          <p className="text-sm text-text-muted">No channels connected yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Platform</TableHead>
                <TableHead>Shop / Seller</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Credential</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {channels.map((channel) => (
                <TableRow key={channel.id}>
                  <TableCell>{channel.platform}</TableCell>
                  <TableCell mono>{channel.shopIdentifier}</TableCell>
                  <TableCell>{statusChip(channel.status)}</TableCell>
                  <TableCell>{statusChip(channel.credentialStatus)}</TableCell>
                  <TableCell align="right">
                    {channel.status !== 'DISCONNECTED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={disconnectMutation.isPending}
                        onClick={() => disconnectMutation.mutate(channel.id)}
                      >
                        Disconnect
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <ConnectChannelModal platform={connectPlatform} onClose={() => setConnectPlatform(null)} />
    </div>
  );
}

/* ----------------------------- Operations console ----------------------------- */

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

  const { data: audit = [] } = useQuery({
    queryKey: ['operations', 'audit'],
    queryFn: async () => (await apiClient.get<AuditLogItem[]>('/api/v1/operations/audit')).data,
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
    <div className="space-y-6" data-testid="operations-console">
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

      <Card>
        <CardHeader title="Recent audit trail" description="Non-ledger mutations with actor and JSON diff" />
        {audit.length === 0 ? (
          <p className="text-sm text-text-muted">No audit entries yet.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Action</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Actor</TableHead>
                <TableHead>Diff</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {audit.slice(0, 20).map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="font-mono text-sm">{row.action}</TableCell>
                  <TableCell className="text-sm">
                    {row.entityType}{' '}
                    <span className="font-mono text-text-muted">{row.entityId.slice(0, 8)}</span>
                  </TableCell>
                  <TableCell className="font-mono text-xs text-text-muted">
                    {row.actorUserId?.slice(0, 8) ?? '—'}
                  </TableCell>
                  <TableCell className="max-w-md truncate font-mono text-xs text-text-muted">
                    {JSON.stringify(row.diff)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

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
  const [activeTab, setActiveTab] = useState<TabId>('profile');

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text">Settings</h1>
        <p className="mt-1 text-sm text-text-muted">
          Manage your company, users, and preferences
        </p>
      </div>

      <div className="flex flex-col gap-6 lg:flex-row">
        <nav className="flex gap-2 overflow-x-auto lg:w-48 lg:flex-col" aria-label="Settings sections">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              aria-current={activeTab === tab.id ? 'page' : undefined}
              className={cn(
                'whitespace-nowrap rounded-md px-3 py-2 text-left text-sm font-medium transition-colors',
                activeTab === tab.id
                  ? 'bg-accent-muted text-accent'
                  : 'text-text-muted hover:bg-surface-overlay hover:text-text'
              )}
            >
              {tab.label}
            </button>
          ))}
        </nav>

        <div className="flex-1">
          {activeTab === 'profile' && <ProfileTab />}
          {activeTab === 'users' && <UsersTab />}
          {activeTab === 'warehouses' && <WarehousesTab />}
          {activeTab === 'inventory' && <InventoryRulesTab />}
          {activeTab === 'documents' && <DocumentsTab />}
          {activeTab === 'billing' && <BillingTab />}
          {activeTab === 'financing' && <FinancingTab />}
          {activeTab === 'security' && <SecuritySsoTab />}
          {activeTab === 'reconciliation' && <ReconciliationTab />}
          {activeTab === 'accounting' && <AccountingSyncTab />}
          {activeTab === 'integrations' && <IntegrationsTab />}
          {activeTab === 'operations' && <OperationsConsoleTab />}
          {activeTab === 'costCenters' && <CostCentersRequisitionsTab />}
        </div>
      </div>
    </div>
  );
}
