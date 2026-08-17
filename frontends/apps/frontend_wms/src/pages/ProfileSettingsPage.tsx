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
import { useTranslation } from 'react-i18next';
import { LanguageSelect } from '@/components/layout/LanguageSelect';
import { normalizeLanguage, type SupportedLanguage } from '@/lib/i18n';

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
  const { t } = useTranslation();
  const user = useSessionStore((s) => s.user);
  const setAvatarUrl = useSessionStore((s) => s.setAvatarUrl);
  const hasRole = useSessionStore((s) => s.hasRole);
  const isAdmin = hasRole('OWNER', 'ADMIN');
  const densityMode = usePreferencesStore((s) => s.densityMode);
  const setDensityMode = usePreferencesStore((s) => s.setDensityMode);
  const language = usePreferencesStore((s) => s.language);
  const setLanguage = usePreferencesStore((s) => s.setLanguage);

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
        if (me.localeLanguage) {
          const next = me.localeLanguage.startsWith('es')
            ? 'es'
            : me.localeLanguage.startsWith('fr')
              ? 'fr'
              : 'en';
          setLanguage(next);
        }
        setOrg(me);
      })
      .catch(() => undefined);
  }, [setDensityMode, setLanguage, user?.displayName]);

  const profileMutation = useMutation({
    mutationFn: async () => {
      const nextLang = usePreferencesStore.getState().language;
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
        preferredLanguage: nextLang,
        localeLanguage: nextLang,
      });
      return nextLang;
    },
    onSuccess: (nextLang) => {
      setLanguage(nextLang);
      setSaved(true);
    },
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
      setPasswordMsg(t('profile.passwordUpdated'));
    },
    onError: () => setPasswordMsg(t('profile.passwordFailed')),
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
        <h1 className="text-2xl font-bold text-text">{t('profile.title')}</h1>
        <p className="mt-1 text-sm text-text-muted">{t('profile.subtitle')}</p>
        {isAdmin && (
          <p className="mt-2 text-sm text-text-muted">
            {t('profile.adminHint')}{' '}
            <Link to="/settings?tab=users" className="text-accent underline-offset-2 hover:underline">
              {t('profile.openUsersAdmin')}
            </Link>
          </p>
        )}
      </div>

      <Card>
        <CardHeader title={t('profile.photo')} description={t('profile.photoDescription')} />
        <div data-testid="profile-avatar-picker">
          <MediaPicker
            kind="AVATAR"
            label={t('profile.uploadPhotoShort')}
            capture
            previewUrl={user?.avatarUrl}
            onUploaded={async (result) => {
              if (result.contentUrl) setAvatarUrl(result.contentUrl);
            }}
          />
        </div>
      </Card>

      <Card>
        <CardHeader title={t('profile.contact')} description={t('profile.contactDescription')} />
        <form
          data-testid="personal-profile-form"
          className="grid grid-cols-1 gap-4 md:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            const selected = normalizeLanguage(
              (e.currentTarget.querySelector('[data-testid="language-select"]') as HTMLSelectElement | null)
                ?.value,
            );
            setLanguage(selected);
            setOrg((prev) => ({ ...prev, localeLanguage: selected }));
            setSaved(false);
            profileMutation.mutate();
          }}
        >
          <Input
            label={t('profile.displayName')}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
          />
          <Input label={t('profile.email')} value={user?.email ?? ''} disabled />
          <Input
            label={t('profile.phone')}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+1 555 0100"
          />
          <Input
            label={t('profile.country')}
            value={addressCountry}
            onChange={(e) => setAddressCountry(e.target.value)}
            placeholder="US"
            maxLength={2}
          />
          <Input
            label={t('profile.addressLine1')}
            value={addressLine1}
            onChange={(e) => setAddressLine1(e.target.value)}
            className="md:col-span-2"
          />
          <Input
            label={t('profile.addressLine2')}
            value={addressLine2}
            onChange={(e) => setAddressLine2(e.target.value)}
            className="md:col-span-2"
          />
          <Input label={t('profile.city')} value={addressCity} onChange={(e) => setAddressCity(e.target.value)} />
          <Input
            label={t('profile.region')}
            value={addressRegion}
            onChange={(e) => setAddressRegion(e.target.value)}
          />
          <Input
            label={t('profile.postalCode')}
            value={addressPostalCode}
            onChange={(e) => setAddressPostalCode(e.target.value)}
          />
          <Select
            label={t('profile.uiDensity')}
            value={densityMode}
            onChange={(e) => setDensityMode(e.target.value as DensityMode)}
            data-testid="ui-density-select"
          >
            <option value="compact">{t('profile.densityCompact')}</option>
            <option value="cozy">{t('profile.densityComfortable')}</option>
            <option value="spacious">{t('profile.densitySpacious')}</option>
          </Select>
          <LanguageSelect
            value={org.localeLanguage ?? language}
            onChange={(lng: SupportedLanguage) => {
              setLanguage(lng);
              setOrg((prev) => ({ ...prev, localeLanguage: lng }));
            }}
          />
          <label className="flex items-center gap-2 pt-6 text-sm text-text" htmlFor="profile-mfa-enabled">
            <input
              id="profile-mfa-enabled"
              type="checkbox"
              checked={mfaEnabled}
              onChange={(e) => setMfaEnabled(e.target.checked)}
              data-testid="mfa-enabled-checkbox"
            />
            {t('profile.mfaEnabled')}
          </label>
          <div className="flex items-center gap-3 md:col-span-2">
            <Button type="submit" loading={profileMutation.isPending} data-testid="save-personal-settings">
              {t('profile.savePersonal')}
            </Button>
            {saved && !profileMutation.isPending && (
              <span className="text-sm text-success">{t('profile.saved')}</span>
            )}
          </div>
        </form>
      </Card>

      <Card>
        <CardHeader title={t('profile.password')} description={t('profile.passwordDescription')} />
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
            label={t('profile.currentPassword')}
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
          <Input
            label={t('profile.newPassword')}
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
          <div className="flex items-center gap-3 md:col-span-2">
            <Button type="submit" loading={passwordMutation.isPending}>
              {t('profile.updatePassword')}
            </Button>
            {passwordMsg && <span className="text-sm text-text-muted">{passwordMsg}</span>}
          </div>
        </form>
      </Card>

      <Card data-testid="org-scope-readonly">
        <CardHeader
          title={t('profile.orgScope')}
          description={t('profile.orgScopeDescription')}
        />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{t('profile.roles')}</p>
            <p className="mt-1 text-sm text-text">{(user?.roles ?? []).join(', ') || '—'}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{t('profile.department')}</p>
            <p className="mt-1 text-sm text-text">
              {org.corporateDepartment ?? org.department ?? '—'}
            </p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{t('profile.timezone')}</p>
            <p className="mt-1 text-sm text-text">{org.timezonePreference ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{t('profile.locale')}</p>
            <p className="mt-1 text-sm text-text">{org.localeLanguage ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{t('profile.shift')}</p>
            <p className="mt-1 text-sm text-text">
              {org.shiftScheduleType ?? org.shiftSchedule ?? '—'}
            </p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
              {t('profile.assignedWarehouse')}
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
