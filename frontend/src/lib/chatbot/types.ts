import type { ComponentType } from 'react';

export type SupportNetworkErrorInput = {
  status?: number | null;
  message?: string | null;
  traceId?: string | null;
};

export type TrainingGuard = {
  isTrainingMode: () => boolean;
  recordBlockedMutation: (method: string, url: string) => void;
  onUiAction: (elementLabel: string) => void;
};

/**
 * Optional chatbot / training module surface.
 * Page Info ("i") content is NOT part of this API — see {@code @/lib/pageKnowledge}.
 */
export type ChatbotModuleApi = {
  ChatbotHost: ComponentType;
  recordSupportNetworkError: (input: SupportNetworkErrorInput) => void;
  getTrainingGuard: () => TrainingGuard;
};
