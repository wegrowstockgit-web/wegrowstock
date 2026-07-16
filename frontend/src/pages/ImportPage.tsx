import { ImportWizard } from '@/features/ingestion/ImportWizard';

export function ImportPage({ legacy = false }: { legacy?: boolean }) {
  const mode = legacy ? 'legacy-migration' : 'import';
  return <ImportWizard key={mode} defaultMode={mode} />;
}
