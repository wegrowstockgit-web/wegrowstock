import { useTranslation } from 'react-i18next';
import { Select } from '@/components/ui/Select';
import { SUPPORTED_LANGUAGES, type SupportedLanguage, normalizeLanguage } from '@/lib/i18n';

interface LanguageSelectProps {
  value?: string | null;
  onChange: (language: SupportedLanguage) => void;
  tone?: 'default' | 'inverse';
}

export function LanguageSelect({ value, onChange, tone = 'default' }: LanguageSelectProps) {
  const { t } = useTranslation();
  return (
    <Select
      label={t('profile.languageHelp')}
      tone={tone}
      value={normalizeLanguage(value)}
      onChange={(e) => onChange(normalizeLanguage(e.target.value))}
      data-testid="language-select"
    >
      {SUPPORTED_LANGUAGES.map((code) => (
        <option key={code} value={code}>
          {t(`languages.${code}`)}
        </option>
      ))}
    </Select>
  );
}
