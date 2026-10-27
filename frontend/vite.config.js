import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
// Backend for `npm run dev`; same paths as the nginx proxy in production
const backend = process.env.VITE_DEV_BACKEND || 'http://localhost:8080'

// Behind the proxy the app is same-origin, like behind nginx: drop the browser Origin so the
// backend CORS check does not reject the dev server port (e.g. 403 on /auth/refresh).
const sameOrigin = (target, options = {}) => ({
  target,
  ...options,
  configure: (proxy) => {
    proxy.on('proxyReq', (req) => req.removeHeader('origin'))
    proxy.on('proxyReqWs', (req) => req.removeHeader('origin'))
  },
})

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': sameOrigin(backend),
      '/auth': sameOrigin(backend),
      '/actuator': sameOrigin(backend),
      '/ws-asaltamontes': sameOrigin(backend, { ws: true }),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    restoreMocks: true,
  },
})
