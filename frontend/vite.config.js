import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
// Backend for `npm run dev`; same paths as the nginx proxy in production
const backend = process.env.VITE_DEV_BACKEND || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': backend,
      '/auth': backend,
      '/actuator': backend,
      '/ws-asaltamontes': { target: backend, ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    restoreMocks: true,
  },
})
