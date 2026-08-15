import type { ComponentType } from 'react';
import type { ChatbotModuleApi, SupportNetworkErrorInput } from '../types';

function ChatbotHost(): null {
  return null;
}

function recordSupportNetworkError(_input: SupportNetworkErrorInput): void {
  // no-op when chatbot module is disabled or absent
}

/** Stub surface — chatbot only. Page help uses {@code @/lib/pageKnowledge}. */
export const chatbotStub: ChatbotModuleApi = {
  ChatbotHost: ChatbotHost as ComponentType,
  recordSupportNetworkError,
};

export { ChatbotHost, recordSupportNetworkError };
