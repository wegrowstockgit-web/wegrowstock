import { defineConfig } from 'vite';
import { fileURLToPath, URL } from 'node:url';

/** Separate IIFE build for the Workbox service worker → dist/sw.js */
export default defineConfig({
  build: {
    emptyOutDir: false,
    outDir: 'dist',
    lib: {
      entry: fileURLToPath(new URL('./src/service-worker.ts', import.meta.url)),
      name: 'invsysSw',
      formats: ['iife'],
      fileName: () => 'sw.js',
    },
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
        entryFileNames: 'sw.js',
      },
    },
    sourcemap: false,
    minify: true,
  },
});
