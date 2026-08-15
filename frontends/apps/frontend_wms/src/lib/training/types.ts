import type { ComponentType } from 'react';

export type TrainingGuard = {
  isTrainingMode: () => boolean;
  recordBlockedMutation: (method: string, url: string) => void;
  onUiAction: (elementLabel: string) => void;
};

export type TrainingModuleApi = {
  TrainingHost: ComponentType;
  getTrainingGuard: () => TrainingGuard;
};
