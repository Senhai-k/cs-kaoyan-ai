import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const noCacheHeaders = {
  'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
  Pragma: 'no-cache',
  Expires: '0'
};

const noCachePlugin = () => ({
  name: 'dev-no-cache',
  configureServer(server: { middlewares: { use: (handler: (_request: unknown, response: { setHeader: (name: string, value: string) => void }, next: () => void) => void) => void } }) {
    server.middlewares.use((_request, response, next) => {
      Object.entries(noCacheHeaders).forEach(([name, value]) => response.setHeader(name, value));
      next();
    });
  }
});

export default defineConfig({
  plugins: [react(), noCachePlugin()],
  server: {
    port: 5173,
    headers: noCacheHeaders,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18888',
        changeOrigin: true
      }
    }
  }
});
