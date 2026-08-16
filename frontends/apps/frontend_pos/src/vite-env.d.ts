/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_DEMO_STORE_LOCATION_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
