/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string;
  readonly VITE_ENABLE_CHATBOT?: string;
  readonly VITE_ENABLE_TRAINING?: string;
  readonly VITE_ENABLE_PRODUCTS?: string;
  readonly VITE_ENABLE_PURCHASING?: string;
  readonly VITE_ENABLE_SALES?: string;
  readonly VITE_ENABLE_FULFILLMENT?: string;
  readonly VITE_ENABLE_FINTECH?: string;
  readonly VITE_ENABLE_MESH?: string;
}

interface Window {
  /** Playwright / ops override; {@code false} skips chatbot mount (see featureFlags). */
  __INVSYS_CHATBOT__?: boolean;
  /** Playwright / ops override; {@code false} skips training host mount. */
  __INVSYS_TRAINING__?: boolean;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
