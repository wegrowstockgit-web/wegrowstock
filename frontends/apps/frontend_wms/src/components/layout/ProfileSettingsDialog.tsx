import { MediaPicker } from '@/components/ui/MediaPicker';
import { Modal } from '@/components/ui/Modal';
import { useSessionStore } from '@/stores/session';

interface ProfileSettingsDialogProps {
  open: boolean;
  onClose: () => void;
}

export function ProfileSettingsDialog({ open, onClose }: ProfileSettingsDialogProps) {
  const user = useSessionStore((s) => s.user);
  const setAvatarUrl = useSessionStore((s) => s.setAvatarUrl);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Profile Settings"
      description="Update your profile photo for the office header"
    >
      <div className="space-y-4 px-6 py-4" data-testid="profile-settings-dialog">
        <div>
          <p className="mb-1 text-sm font-medium text-text">{user?.displayName}</p>
          <p className="text-xs text-text-muted">{user?.email}</p>
          <p className="mt-1 text-xs text-text-muted">{(user?.roles ?? []).join(', ')}</p>
        </div>
        <MediaPicker
          kind="AVATAR"
          label="Upload profile photo"
          capture
          previewUrl={user?.avatarUrl}
          onUploaded={async (result) => {
            // Avatar path compresses to ≤512px WebP via uploadViaPresign / USER_AVATAR.
            if (result.contentUrl) setAvatarUrl(result.contentUrl);
          }}
        />
        <p className="text-xs text-text-muted">
          Photos are compressed on-device before upload for a fast header avatar.
        </p>
      </div>
    </Modal>
  );
}
