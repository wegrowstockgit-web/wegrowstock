import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { SettingsSubpageShell } from '@/components/layout/SettingsSubpageShell';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { Select } from '@/components/ui/Select';
import { usePreferencesStore, type DensityMode } from '@/stores/preferencesStore';
import { useSessionStore } from '@/stores/session';

type MeProfile = {
  displayName?: string;
  phone?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  addressCity?: string | null;
  addressRegion?: string | null;
  addressPostalCode?: string | null;
  addressCountry?: string | null;
  mfaEnabled?: boolean;
  uiDensityPreference?: string | null;
  assignedWarehouseId?: string | null;
  corporateDepartment?: string | null;
  department?: string | null;
  timezonePreference?: string | null;
  localeLanguage?: string | null;
  shiftScheduleType?: string | null;
  shiftSchedule?: string | null;
  warehouseIds?: string[];
};

function densityToApi(mode: DensityMode): string {
  if (mode === 'compact') return 'COMPACT';
  if (mode === 'spacious') return 'SPACIOUS';
  return 'COMFORTABLE';
}

function densityFromApi(value?: string | null): DensityMode | null {
  if (!value) return null;
  const v = value.toUpperCase();
  if (v === 'COMPACT') return 'compact';
  if (v === 'SPACIOUS') return 'spacious';
  if (v === 'COMFORTABLE') return 'cozy';
  return null;
}

/**
 * Self-service personal settings — available to every authenticated office role.
 * Organizational fields are read-only badges here (admin edits via Settings → Users).
 */
export function ProfileSettingsPage() {
  const user = useSessionStore((s) => s.user);
  const setAvatarUrl = useSessionStore((s) => s.setAvatarUrl);
  const hasRole = useSessionStore((s) => s.hasRole);
  const isAdmin = hasRole('OWNER', 'ADMIN');
  const densityMode = usePreferencesStore((s) => s.densityMode);
  const setDensityMode = usePreferencesStore((s) => s.setDensityMode);

  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [phone, setPhone] = useState('');
  const [addressLine1, setAddressLine1] = useState('');
  const [addressLine2, setAddressLine2] = useState('');
  const [addressCity, setAddressCity] = useState('');
  const [addressRegion, setAddressRegion] = useState('');
  const [addressPostalCode, setAddressPostalCode] = useState('');
  const [addressCountry, setAddressCountry] = useState('');
  const [mfaEnabled, setMfaEnabled] = useState(false);
  const [saved, setSaved] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordMsg, setPasswordMsg] = useState('');
  const [org, setOrg] = useState<MeProfile>({});

  const { data: warehouses = [] } = useQuery({
    queryKey: ['locations', 'warehouses-profile-readonly'],
    queryFn: async () => {
      const rows = (await apiClient.get<TenantLocation[]>('/api/v1/locations')).data;
      return rows.filter((l) => l.type === 'WAREHOUSE' || l.type === 'VEHICLE');
    },
  });

  useEffect(() => {
    void apiClient
      .get<MeProfile>('/api/v1/auth/me')
      .then((res) => {
        const me = res.data;
        setDisplayName(me.displayName ?? user?.displayName ?? '');
        setPhone(me.phone ?? '');
        setAddressLine1(me.addressLine1 ?? '');
        setAddressLine2(me.addressLine2 ?? '');
        setAddressCity(me.addressCity ?? '');
        setAddressRegion(me.addressRegion ?? '');
        setAddressPostalCode(me.addressPostalCode ?? '');
        setAddressCountry(me.addressCountry ?? '');
        setMfaEnabled(Boolean(me.mfaEnabled));
        const mapped = densityFromApi(me.uiDensityPreference);
        if (mapped) setDensityMode(mapped);
        setOrg(me);
      })
      .catch(() => undefined);
  }, [setDensityMode, user?.displayName]);

  const profileMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch('/api/v1/users/me/profile', {
        displayName: displayName || null,
        phone: phone || null,
        addressLine1: addressLine1 || null,
        addressLine2: addressLine2 || null,
        addressCity: addressCity || null,
        addressRegion: addressRegion || null,
        addressPostalCode: addressPostalCode || null,
        addressCountry: addressCountry || null,
        mfaEnabled,
        uiDensityPreference: densityToApi(densityMode),
      });
    },
    onSuccess: () => setSaved(true),
  });

  const passwordMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/users/me/password', {
        currentPassword,
        newPassword,
      });
    },
    onSuccess: () => {
      setCurrentPassword('');
      setNewPassword('');
      setPasswordMsg('Password updated. Sign in again on other devices.');
    },
    onError: () => setPasswordMsg('Could not change password. Check your current password.'),
  });

  const assigned = warehouses.find(
    (w) => w.id === (org.assignedWarehouseId ?? undefined),
  );
  const assignedBadges =
    (org.warehouseIds?.length ? org.warehouseIds : user?.warehouseIds) ?? [];

  return (
    <SettingsSubpageShell testId="profile-settings-page">
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-6 sm:px-6">
      <div>
        <h1 className="text-2xl font-bold text-text">Personal settings</h1>
        <p className="mt-1 text-sm text-text-muted">
          Manage your photo, contact details, password, and UI preferences. Organizational access
          is controlled by workspace admins.
        </p>
        {isAdmin && (
          <p className="mt-2 text-sm text-text-muted">
            Need to edit roles or warehouse access?{' '}
            <Link to="/settings?tab=users" className="text-accent underline-offset-2 hover:underline">
              Open Users admin
            </Link>
          </p>
        )}
      </div>

      <Card>
        <CardHeader title="Profile photo" description="Shown in the office header" />
        <div data-testid="profile-avatar-picker">
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
      </Card>

      <Card>
        <CardHeader title="Contact information" description="Visible to your workspace admins" />
        <form
          data-testid="personal-profile-form"
          className="grid grid-cols-1 gap-4 md:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            setSaved(false);
            profileMutation.mutate();
          }}
        >
          <Input
            label="Display name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
          />
          <Input label="Email" value={user?.email ?? ''} disabled />
          <Input
            label="Phone"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+1 555 0100"
          />
          <Input
            label="Country"
            value={addressCountry}
            onChange={(e) => setAddressCountry(e.target.value)}
            placeholder="US"
            maxLength={2}
          />
          <Input
            label="Address line 1"
            value={addressLine1}
            onChange={(e) => setAddressLine1(e.target.value)}
            className="md:col-span-2"
          />
          <Input
            label="Address line 2"
            value={addressLine2}
            onChange={(e) => setAddressLine2(e.target.value)}
            className="md:col-span-2"
          />
          <Input label="City" value={addressCity} onChange={(e) => setAddressCity(e.target.value)} />
          <Input
            label="State / region"
            value={addressRegion}
            onChange={(e) => setAddressRegion(e.target.value)}
          />
          <Input
            label="Postal code"
            value={addressPostalCode}
            onChange={(e) => setAddressPostalCode(e.target.value)}
          />
          <Select
            label="UI density"
            value={densityMode}
            onChange={(e) => setDensityMode(e.target.value as DensityMode)}
            data-testid="ui-density-select"
          >
            <option value="compact">Compact</option>
            <option value="cozy">Comfortable</option>
            <option value="spacious">Spacious</option>
          </Select>
          <label className="flex items-center gap-2 pt-6 text-sm text-text" htmlFor="profile-mfa-enabled">
            <input
              id="profile-mfa-enabled"
              type="checkbox"
              checked={mfaEnabled}
              onChange={(e) => setMfaEnabled(e.target.checked)}
              data-testid="mfa-enabled-checkbox"
            />
            MFA enabled (preference flag)
          </label>
          <div className="flex items-center gap-3 md:col-span-2">
            <Button type="submit" loading={profileMutation.isPending}>
              Save personal settings
            </Button>
            {saved && !profileMutation.isPending && (
              <span className="text-sm text-success">Saved</span>
            )}
          </div>
        </form>
      </Card>

      <Card>
        <CardHeader title="Password" description="Changing password signs out other sessions" />
        <form
          data-testid="change-password-form"
          className="grid grid-cols-1 gap-4 md:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            setPasswordMsg('');
            passwordMutation.mutate();
          }}
        >
          <Input
            label="Current password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
          <Input
            label="New password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
          <div className="flex items-center gap-3 md:col-span-2">
            <Button type="submit" loading={passwordMutation.isPending}>
              Update password
            </Button>
            {passwordMsg && <span className="text-sm text-text-muted">{passwordMsg}</span>}
          </div>
        </form>
      </Card>

      <Card data-testid="org-scope-readonly">
        <CardHeader
          title="Organizational scope"
          description="Read-only — managed by OWNER / ADMIN"
        />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Roles</p>
            <p className="mt-1 text-sm text-text">{(user?.roles ?? []).join(', ') || '—'}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Department</p>
            <p className="mt-1 text-sm text-text">
              {org.corporateDepartment ?? org.department ?? '—'}
            </p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Timezone</p>
            <p className="mt-1 text-sm text-text">{org.timezonePreference ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Locale</p>
            <p className="mt-1 text-sm text-text">{org.localeLanguage ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Shift</p>
            <p className="mt-1 text-sm text-text">
              {org.shiftScheduleType ?? org.shiftSchedule ?? '—'}
            </p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
              Assigned warehouse
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              {assigned ? (
                <span
                  className="rounded-md bg-surface-overlay px-2 py-1 text-xs font-medium text-text"
                  data-testid="assigned-warehouse-badge"
                >
                  {assigned.code} — {assigned.name}
                </span>
              ) : (
                <span className="text-sm text-text-muted">—</span>
              )}
              {assignedBadges
                .filter((id) => id !== assigned?.id)
                .map((id) => {
                  const wh = warehouses.find((w) => w.id === id);
                  return (
                    <span
                      key={id}
                      className="rounded-md border border-border px-2 py-1 text-xs text-text-muted"
                    >
                      {wh ? `${wh.code}` : id.slice(0, 8)}
                    </span>
                  );
                })}
            </div>
          </div>
        </div>
      </Card>
    </div>
    </SettingsSubpageShell>
  );
}
