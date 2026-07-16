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
        'src/lib/terminalPasskey.ts',
        'src/hooks/useBarcodeScanner.ts',
        'src/hooks/useDensity.ts',
        'src/stores/offlineStore.ts',
        'src/stores/preferencesStore.ts',
        'src/stores/gridColumnStore.ts',
        'src/offline/mutationQueue.ts',
        'src/features/fulfillment/QuarantineReview.tsx',
        'src/features/fulfillment/ScannerView.tsx',
        'src/features/settings/WarehouseVisualizer.tsx',
        'src/features/ingestion/ImportWizard.tsx',
        'src/components/ui/primitives/VirtualizedTable.tsx',
        'src/components/ui/DensityToggle.tsx',
        'src/components/ui/ColumnVisibilityMenu.tsx',
        'src/components/ui/Table.tsx',
        'src/hooks/useClientSort.ts',
      ],
      // CameraCapture / MediaPicker / TerminalPinPad: unit + Playwright e2e (not in threshold set).
      thresholds: {
        lines: 85,
        functions: 85,
        branches: 70,
        statements: 85,
      },
    },
  },
});
