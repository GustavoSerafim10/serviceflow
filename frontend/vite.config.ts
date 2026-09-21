/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // O navegador só fala com a própria origem (localhost:5173); o Vite encaminha /api para a API Java.
    // Assim não há CORS a configurar no backend. Em produção, o nginx faz o mesmo papel (ver nginx.conf).
    proxy: { '/api': process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
  },
})
