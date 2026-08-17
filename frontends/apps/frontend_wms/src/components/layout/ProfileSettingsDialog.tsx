import { useTranslation } from 'react-i18next';
import { MediaPicker } from '@/components/ui/MediaPicker';
import { Modal } from '@/components/ui/Modal';
import { LanguageSelect } from '@/components/layout/LanguageSelect';
import { useSessionStore } from '@/stores/session';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { apiClient } from '@/api/client';
import { type SupportedLanguage } from '@/lib/i18n';

interface ProfileSettingsDialogProps {
  open: boolean;
  onClose: () => void;
}

export function ProfileSettingsDialog({ open, onClose }: ProfileSettingsDialogProps) {
  const { t } = useTranslation();
  const user = useSessionStore((s) => s.user);
  const setAvatarUrl = useSessionStore((s) => s.setAvatarUrl);
  const applyMeProfile = useSessionStore((s) => s.applyMeProfile);
  const language = usePreferencesStore((s) => s.language);
  const setLanguage = usePreferencesStore((s) => s.setLanguage);

  const persistLanguage = async (next: SupportedLanguage) => {
    setLanguage(next);
    try {
      await apiClient.patch('/api/v1/users/me/profile', { preferredLanguage: next, localeLanguage: next });
      if (user) {
        applyMeProfile({
          userId: user.id,
          email: user.email,
          displayName: user.displayName,
          roles: [...user.roles],
          warehouseIds: user.warehouseIds ? [...user.warehouseIds] : [],
          avatarUrl: user.avatarUrl,
          tenantId: user.tenantId,
          grantedPermissions: user.grantedPermissions ? [...user.grantedPermissions] : [],
          isSuperAdmin: user.isSuperAdmin,
          enabledModules: user.enabledModules ? [...user.enabledModules] : [],
          localeLanguage: next,
          tier: user.tier,
        });
      }
    } catch {
      // keep local preference even if the profile patch fails offline
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('common.profileSettings')}
      description={t('profile.photoHelp')}
    >
      <div className="space-y-4 px-6 py-4" data-testid="profile-settings-dialog">
        <div>
          <p className="mb-1 text-sm font-medium text-text">{user?.displayName}</p>
          <p className="text-xs text-text-muted">{user?.email}</p>
          <p className="mt-1 text-xs text-text-muted">{(user?.roles ?? []).join(', ')}</p>
        </div>
        <LanguageSelect value={user?.localeLanguage ?? language} onChange={(lng) => void persistLanguage(lng)} />
        <MediaPicker
          kind="AVATAR"
          label={t('profile.uploadPhoto')}
          capture
          previewUrl={user?.avatarUrl}
          onUploaded={async (result) => {
            // Avatar path compresses to ≤512px WebP via uploadViaPresign / USER_AVATAR.
            if (result.contentUrl) setAvatarUrl(result.contentUrl);
          }}
        />
        <p className="text-xs text-text-muted">
          {t('profile.photoCompressed')}
        </p>
      </div>
    </Modal>
  );
}
