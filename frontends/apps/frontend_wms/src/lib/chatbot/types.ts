import type { ComponentType } from 'react';

export type SupportNetworkErrorInput = {
  status?: number | null;
  message?: string | null;
  traceId?: string | null;
};

/**
 * Optional chatbot module surface.
 * Page Info ("i") content is NOT part of this API — see {@code @/lib/pageKnowledge}.
 * Flight Simulator is {@code @/lib/training/*}.
 */
export type ChatbotModuleApi = {
  ChatbotHost: ComponentType;
  recordSupportNetworkError: (input: SupportNetworkErrorInput) => void;
};
