import type { ReactElement } from 'react';
import { Suspense, lazy } from 'react';
import { recordSupportNetworkError } from './supportNetworkTelemetry';

const SupportAssistantWidget = lazy(() =>
  import('./SupportAssistantWidget').then((m) => ({ default: m.SupportAssistantWidget })),
);
const OnboardingTourHost = lazy(() =>
  import('./OnboardingTourHost').then((m) => ({ default: m.OnboardingTourHost })),
);
const TourOrchestrator = lazy(() =>
  import('./TourOrchestrator').then((m) => ({ default: m.TourOrchestrator })),
);

/**
 * Mounts Support Co-Pilot FAB, onboarding tour, and tour orchestrator.
 * Flight Simulator lives in {@code src/modules/training}.
 * Page Info ("i") knowledge stays in {@code @/lib/pageKnowledge}.
 */
export function ChatbotHost(): ReactElement {
  return (
    <Suspense fallback={null}>
      <SupportAssistantWidget />
      <OnboardingTourHost />
      <TourOrchestrator />
    </Suspense>
  );
}

export { recordSupportNetworkError };
