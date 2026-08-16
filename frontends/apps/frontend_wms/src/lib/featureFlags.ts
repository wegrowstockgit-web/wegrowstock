/**
 * Support Co-Pilot / training / onboarding tour.
 *
 * Build-time: {@code VITE_ENABLE_CHATBOT=false} or {@code npm run chatbot:disable}
 * (writes {@code .chatbot-disabled} + stubs {@code @/lib/chatbot/active}).
 * Removing {@code src/modules/chatbot} also forces the stub — core still compiles.
 */
export const IS_CHATBOT_ENABLED = import.meta.env.VITE_ENABLE_CHATBOT !== 'false';

/** Flight Simulator / training sandbox module. */
export const IS_TRAINING_ENABLED = import.meta.env.VITE_ENABLE_TRAINING !== 'false';

/**
 * Runtime gate used by {@code App.tsx}. Honors {@link IS_CHATBOT_ENABLED} and an optional
 * Playwright override {@code window.__INVSYS_CHATBOT__ = false}.
 */
export function isChatbotEnabled(): boolean {
  if (!IS_CHATBOT_ENABLED) {
    return false;
  }
  if (typeof window !== 'undefined' && window.__INVSYS_CHATBOT__ === false) {
    return false;
  }
  return true;
}

export function isTrainingEnabled(): boolean {
  if (!IS_TRAINING_ENABLED) {
    return false;
  }
  if (typeof window !== 'undefined' && window.__INVSYS_TRAINING__ === false) {
    return false;
  }
  return true;
}

/**
 * Feature-sliced modules registered in {@code src/lib/router/appModules.tsx}.
 * Set {@code VITE_ENABLE_<MODULE>=false} to omit routes + sidebar items at build time.
 */
export const FEATURE_MODULE_FLAGS = {
  products: import.meta.env.VITE_ENABLE_PRODUCTS !== 'false',
  purchasing: import.meta.env.VITE_ENABLE_PURCHASING !== 'false',
  sales: import.meta.env.VITE_ENABLE_SALES !== 'false',
  fulfillment: import.meta.env.VITE_ENABLE_FULFILLMENT !== 'false',
  fintech: import.meta.env.VITE_ENABLE_FINTECH !== 'false',
  mesh: import.meta.env.VITE_ENABLE_MESH !== 'false',
} as const;

