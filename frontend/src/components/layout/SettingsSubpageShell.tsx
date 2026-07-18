import type { ReactNode } from 'react';
import { ScrollFadePort } from '@/components/ui/ScrollFadePort';

/**
 * Full-height settings sub-route shell: owns the only vertical scrollport,
 * hides native scrollbar chrome, and shows fold cues like the icon rail.
 */
export function SettingsSubpageShell({
  children,
  testId,
}: {
  children: ReactNode;
  testId: string;
}) {
  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden" data-testid={testId}>
      <ScrollFadePort
        data-testid={`${testId}-scroll`}
        shellClassName="min-h-0 flex-1"
        className="h-full overflow-y-auto overflow-x-hidden"
      >
        {children}
      </ScrollFadePort>
    </div>
  );
}
