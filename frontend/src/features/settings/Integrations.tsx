import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing } from 'lucide-react';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';
import { ShopifyIntegration } from '@/features/settings/ShopifyIntegration';

interface AlertPreferences {
  alertEmail: string | null;
  slackWebhookUrl: string | null;
  slackConfigured: boolean;
  emailConfigured: boolean;
}

function isValidEmail(value: string): boolean {
  if (!value.trim()) return true;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

function isValidHttpUrl(value: string): boolean {
  if (!value.trim()) return true;
  try {
    const url = new URL(value.trim());
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

function SystemAlertsCard() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [alertEmail, setAlertEmail] = useState('');
  const [slackWebhookUrl, setSlackWebhookUrl] = useState('');
  const [emailError, setEmailError] = useState('');
  const [slackError, setSlackError] = useState('');

  const { data: prefs } = useQuery({
    queryKey: ['settings', 'alert-preferences'],
    queryFn: async () =>
      (await apiClient.get<AlertPreferences>('/api/v1/settings/alert-preferences')).data,
    retry: false,
  });

  useEffect(() => {
    if (prefs) {
      setAlertEmail(prefs.alertEmail ?? '');
      // Never prefill masked webhook — user must re-enter to rotate.
      setSlackWebhookUrl('');
    }
  }, [prefs]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const body: { alertEmail: string; slackWebhookUrl?: string } = {
        alertEmail: alertEmail.trim(),
      };
      if (slackWebhookUrl.trim()) {
        body.slackWebhookUrl = slackWebhookUrl.trim();
      }
      await apiClient.put('/api/v1/settings/alert-preferences', body);
    },
    onSuccess: () => {
      toast('Alert preferences saved', { tone: 'success' });
      setSlackWebhookUrl('');
      void queryClient.invalidateQueries({ queryKey: ['settings', 'alert-preferences'] });
    },
    onError: () => toast('Could not save alert preferences', { tone: 'danger' }),
  });

  const testMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/alert-preferences/test');
    },
    onSuccess: () => toast('Test alert dispatched', { tone: 'success' }),
    onError: () => toast('Test alert failed', { tone: 'danger' }),
  });

  const validateAndSave = () => {
    const nextEmailError = isValidEmail(alertEmail) ? '' : 'Enter a valid email address';
    const nextSlackError = isValidHttpUrl(slackWebhookUrl)
      ? ''
      : 'Enter a valid http(s) Slack webhook URL';
    setEmailError(nextEmailError);
    setSlackError(nextSlackError);
    if (nextEmailError || nextSlackError) {
      return;
    }
    saveMutation.mutate();
  };

  return (
    <Card data-testid="system-alerts-card">
      <CardHeader
        title="System Alerts & Diagnostics"
        description="Rate-limited Slack and email alerts when integrations fail (signature, OAuth, 401/403/500)"
      />
      <div className="mb-4 flex flex-wrap items-center gap-2 text-xs text-text-muted">
        <BellRing className="h-3.5 w-3.5" aria-hidden />
        {prefs?.emailConfigured ? 'Email configured' : 'Email not set'}
        {' · '}
        {prefs?.slackConfigured
          ? `Slack configured (${prefs.slackWebhookUrl ?? '••••'})`
          : 'Slack not set'}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <Input
            label="IT Contact Email"
            type="email"
            value={alertEmail}
            onChange={(e) => {
              setAlertEmail(e.target.value);
              setEmailError('');
            }}
            placeholder="ops@yourcompany.com"
            autoComplete="email"
          />
          {emailError && <p className="mt-1 text-xs text-danger">{emailError}</p>}
        </div>
        <div>
          <Input
            label="Slack Webhook URL"
            type="url"
            value={slackWebhookUrl}
            onChange={(e) => {
              setSlackWebhookUrl(e.target.value);
              setSlackError('');
            }}
            placeholder={
              prefs?.slackConfigured
                ? 'Enter a new webhook URL to rotate'
                : 'https://hooks.slack.com/services/…'
            }
            autoComplete="off"
          />
          {slackError && <p className="mt-1 text-xs text-danger">{slackError}</p>}
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button loading={saveMutation.isPending} onClick={validateAndSave}>
          Save alert settings
        </Button>
        <Button
          variant="secondary"
          loading={testMutation.isPending}
          onClick={() => testMutation.mutate()}
          disabled={!prefs?.emailConfigured && !prefs?.slackConfigured}
          title={
            !prefs?.emailConfigured && !prefs?.slackConfigured
              ? 'Configure email or Slack first'
              : 'Fire a mock IntegrationFailureEvent'
          }
        >
          Test Alert
        </Button>
      </div>
    </Card>
  );
}

/**
 * Integrations settings surface: channel connections + system alert preferences.
 */
export function Integrations() {
  return (
    <div className="space-y-6" data-testid="integrations-settings">
      <SystemAlertsCard />
      <ShopifyIntegration />
    </div>
  );
}
