import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import legacy from '@vitejs/plugin-legacy';
import fs from 'node:fs';
import { fileURLToPath, URL } from 'node:url';

const rootDir = fileURLToPath(new URL('.', import.meta.url));
const chatbotDisabledMarker = fileURLToPath(new URL('./.chatbot-disabled', import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, rootDir, '');
  const chatbotDisabled =
    env.VITE_ENABLE_CHATBOT === 'false' || fs.existsSync(chatbotDisabledMarker);

  return {
    plugins: [
      react(),
      legacy({ targets: ['defaults', 'not IE 11'] }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    define: chatbotDisabled
      ? {
          'import.meta.env.VITE_ENABLE_CHATBOT': JSON.stringify('false'),
        }
      : undefined,
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          // Forward Set-Cookie / Cookie for HttpOnly session auth.
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq, req) => {
              if (req.headers.cookie) {
                proxyReq.setHeader('cookie', req.headers.cookie);
              }
            });
          },
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: true,
    },
  };
});
