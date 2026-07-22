import type { ComponentType } from 'react';
import type { TrainingGuard, TrainingModuleApi } from '../types';

const noopTrainingGuard: TrainingGuard = {
  isTrainingMode: () => false,
  recordBlockedMutation: () => undefined,
  onUiAction: () => undefined,
};

function TrainingHost(): null {
  return null;
}

export const trainingModuleApi: TrainingModuleApi = {
  TrainingHost: TrainingHost as ComponentType,
  getTrainingGuard: () => noopTrainingGuard,
};

export { TrainingHost };
export function getTrainingGuard(): TrainingGuard {
  return noopTrainingGuard;
}
