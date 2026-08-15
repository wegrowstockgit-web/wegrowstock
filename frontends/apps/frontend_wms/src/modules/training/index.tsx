import type { ReactElement } from 'react';
import { Suspense, lazy } from 'react';
import type { TrainingGuard } from '@/lib/training/types';
import { useTrainingSandboxStore } from './trainingSandboxStore';

const TrainingMissionBanner = lazy(() =>
  import('./TrainingMissionBanner').then((m) => ({ default: m.TrainingMissionBanner })),
);

/** Flight Simulator host — mission banner only (Co-Pilot stays in chatbot module). */
export function TrainingHost(): ReactElement {
  return (
    <Suspense fallback={null}>
      <TrainingMissionBanner />
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

export { useTrainingSandboxStore, TRAINING_SCENARIOS } from './trainingSandboxStore';
