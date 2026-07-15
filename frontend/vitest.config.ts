import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['e2e/**', 'node_modules/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      include: [
        'src/utils/gs1Parser.ts',
        'src/lib/gs1Barcode.ts',
        'src/hooks/useBarcodeScanner.ts',
        'src/stores/offlineStore.ts',
        'src/offline/mutationQueue.ts',
        'src/features/fulfillment/QuarantineReview.tsx',
      ],
      // ScannerView camera/upload paths are covered by Playwright e2e (gs1-composite-scan).
      thresholds: {
        lines: 85,
        functions: 85,
        branches: 68,
        statements: 85,
      },
    },
  },
});
