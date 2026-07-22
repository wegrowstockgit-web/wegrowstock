import type { ComponentType } from 'react';
import type { ChatbotModuleApi, SupportNetworkErrorInput, TrainingGuard } from '../types';

function ChatbotHost(): null {
  return null;
}

const noopTrainingGuard: TrainingGuard = {
  isTrainingMode: () => false,
  recordBlockedMutation: () => undefined,
  onUiAction: () => undefined,
};

function recordSupportNetworkError(_input: SupportNetworkErrorInput): void {
  // no-op when chatbot module is disabled or absent
}

/** Stub surface — chatbot/training only. Page help uses {@code @/lib/pageKnowledge}. */
export const chatbotStub: ChatbotModuleApi = {
  ChatbotHost: ChatbotHost as ComponentType,
  recordSupportNetworkError,
  getTrainingGuard: () => noopTrainingGuard,
};

export { ChatbotHost, recordSupportNetworkError };
export function getTrainingGuard(): TrainingGuard {
  return noopTrainingGuard;
}
