import type { ReactElement } from 'react';
import { Suspense, lazy } from 'react';
import type { TrainingGuard } from '@/lib/chatbot/types';
import { recordSupportNetworkError } from './supportNetworkTelemetry';
import { useTrainingSandboxStore } from './trainingSandboxStore';

const SupportAssistantWidget = lazy(() =>
  import('./SupportAssistantWidget').then((m) => ({ default: m.SupportAssistantWidget })),
);
const TrainingMissionBanner = lazy(() =>
  import('./TrainingMissionBanner').then((m) => ({ default: m.TrainingMissionBanner })),
);
const OnboardingTourHost = lazy(() =>
  import('./OnboardingTourHost').then((m) => ({ default: m.OnboardingTourHost })),
);
const TourOrchestrator = lazy(() =>
  import('./TourOrchestrator').then((m) => ({ default: m.TourOrchestrator })),
);

/**
 * Mounts Support Co-Pilot FAB, training banner, onboarding tour, and tour orchestrator.
 * Page Info ("i") knowledge stays in {@code @/lib/pageKnowledge} — always available.
 */
export function ChatbotHost(): ReactElement {
  return (
    <Suspense fallback={null}>
      <SupportAssistantWidget />
      <TrainingMissionBanner />
      <OnboardingTourHost />
      <TourOrchestrator />
    </Suspense>
  );
}

export function getTrainingGuard(): TrainingGuard {
  return {
    isTrainingMode: () => useTrainingSandboxStore.getState().isTrainingMode(),
    recordBlockedMutation: (method, url) =>
      useTrainingSandboxStore.getState().recordBlockedMutation(method, url),
    onUiAction: (elementLabel) => useTrainingSandboxStore.getState().onUiAction(elementLabel),
  };
}

export { recordSupportNetworkError };
