import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@/api/client';
import type { TenantSettingsMap } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/Toast';
import { cn } from '@/lib/utils';
import {
  POS_CURRENCIES,
  POS_SETTINGS_KEYS,
  normalizePosCurrency,
  type PosCurrency,
} from './posSettingsAccess';

function ToggleRow({
  label,
  description,
  checked,
  onChange,
  testId,
}: {
  label: string;
  description: string;
  checked: boolean;
  onChange: (next: boolean) => void;
  testId: string;
}) {
  return (
    <div className="flex items-start justify-between gap-4 py-1">
      <div>
        <p className="font-medium text-text">{label}</p>
        <p className="mt-0.5 text-sm text-text-muted">{description}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        data-testid={testId}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative h-7 w-12 shrink-0 rounded-full transition-colors',
          checked ? 'bg-accent' : 'bg-surface-overlay',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition-transform',
            checked ? 'left-5' : 'left-0.5',
          )}
        />
      </button>
    </div>
  );
}

export function PosSettingsPanel() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [currency, setCurrency] = useState<PosCurrency>('USD');
  const [enableCfdi, setEnableCfdi] = useState(false);
  const [header, setHeader] = useState('');
  const [footer, setFooter] = useState('');
  const [blindCloseout, setBlindCloseout] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['settings'],
    queryFn: async () => (await apiClient.get<TenantSettingsMap>('/api/v1/settings')).data,
  });

  useEffect(() => {
    if (!data) return;
    setCurrency(normalizePosCurrency(data[POS_SETTINGS_KEYS.defaultCurrency]));
    setEnableCfdi(Boolean(data[POS_SETTINGS_KEYS.enableCfdi]));
    setHeader(typeof data[POS_SETTINGS_KEYS.receiptHeader] === 'string'
      ? String(data[POS_SETTINGS_KEYS.receiptHeader])
      : '');
    setFooter(typeof data[POS_SETTINGS_KEYS.receiptFooter] === 'string'
      ? String(data[POS_SETTINGS_KEYS.receiptFooter])
      : '');
    setBlindCloseout(Boolean(data[POS_SETTINGS_KEYS.requireBlindCloseout]));
  }, [data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch('/api/v1/settings', {
        [POS_SETTINGS_KEYS.defaultCurrency]: currency,
        [POS_SETTINGS_KEYS.enableCfdi]: enableCfdi,
        [POS_SETTINGS_KEYS.receiptHeader]: header,
        [POS_SETTINGS_KEYS.receiptFooter]: footer,
        [POS_SETTINGS_KEYS.requireBlindCloseout]: blindCloseout,
      });
    },
    onSuccess: () => {
      toast(t('settings.retailPos.saved'), { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: () => toast(t('settings.retailPos.saveFailed'), { tone: 'danger' }),
  });

  return (
    <form
      className="space-y-6"
      data-testid="pos-settings-panel"
      onSubmit={(e) => {
        e.preventDefault();
        saveMutation.mutate();
      }}
    >
      <Card data-testid="pos-settings-localization">
        <CardHeader
          title={t('settings.retailPos.localizationTitle')}
          description={t('settings.retailPos.localizationDescription')}
        />
        {isLoading ? (
          <p className="text-sm text-text-muted">{t('settings.retailPos.loading')}</p>
        ) : (
          <div className="space-y-4">
            <Select
              label={t('settings.retailPos.defaultCurrency')}
              value={currency}
              onChange={(e) => setCurrency(normalizePosCurrency(e.target.value))}
              data-testid="pos-default-currency"
            >
              {POS_CURRENCIES.map((code) => (
                <option key={code} value={code}>
                  {code}
                </option>
              ))}
            </Select>
            <ToggleRow
              label={t('settings.retailPos.enableCfdi')}
              description={t('settings.retailPos.enableCfdiHint')}
              checked={enableCfdi}
              onChange={setEnableCfdi}
              testId="pos-enable-cfdi"
            />
          </div>
        )}
      </Card>

      <Card data-testid="pos-settings-receipt">
        <CardHeader
          title={t('settings.retailPos.receiptTitle')}
          description={t('settings.retailPos.receiptDescription')}
        />
        <div className="space-y-4">
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-text">{t('settings.retailPos.receiptHeader')}</span>
            <textarea
              data-testid="pos-receipt-header"
              className="min-h-28 w-full rounded-md border border-border bg-surface-raised p-3 text-sm text-text"
              value={header}
              onChange={(e) => setHeader(e.target.value)}
              placeholder={t('settings.retailPos.receiptHeaderPlaceholder')}
              maxLength={2000}
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-text">{t('settings.retailPos.receiptFooter')}</span>
            <textarea
              data-testid="pos-receipt-footer"
              className="min-h-24 w-full rounded-md border border-border bg-surface-raised p-3 text-sm text-text"
              value={footer}
              onChange={(e) => setFooter(e.target.value)}
              placeholder={t('settings.retailPos.receiptFooterPlaceholder')}
              maxLength={2000}
            />
          </label>
        </div>
      </Card>

      <Card data-testid="pos-settings-security">
        <CardHeader
          title={t('settings.retailPos.securityTitle')}
          description={t('settings.retailPos.securityDescription')}
        />
        <ToggleRow
          label={t('settings.retailPos.blindCloseout')}
          description={t('settings.retailPos.blindCloseoutHint')}
          checked={blindCloseout}
          onChange={setBlindCloseout}
          testId="pos-require-blind-closeout"
        />
      </Card>

      <div className="flex items-center gap-3">
        <Button type="submit" loading={saveMutation.isPending} data-testid="pos-settings-save">
          {t('settings.retailPos.save')}
        </Button>
        {saveMutation.isSuccess && !saveMutation.isPending && (
          <span className="text-sm text-success">{t('settings.saved')}</span>
        )}
      </div>
    </form>
  );
}
