/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    // O navegador só fala com a própria origem (localhost:5173); o Vite encaminha /api para a API Java.
    // Assim não há CORS a configurar no backend. Em produção, o nginx faz o mesmo papel (ver nginx.conf).
    proxy: { '/api': process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080' },
  },
  // `--mode embed`: página ÚNICA para publicar (artefato). Saída em um arquivo JS clássico (IIFE), sem divisão de
  // código, para o scripts/pack-embed.mjs embutir tudo num só HTML. Os demais modos usam a saída padrão do Vite.
  build: mode === 'embed'
    ? {
        outDir: 'dist-embed',
        cssCodeSplit: false,
        rollupOptions: {
          output: { format: 'iife', inlineDynamicImports: true, entryFileNames: 'assets/app.js', assetFileNames: 'assets/app[extname]' },
        },
      }
    : undefined,
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    // Padrão (5s) é curto demais quando os arquivos de teste rodam em paralelo numa máquina modesta:
    // fica todo mundo disputando CPU e um teste correto estoura o tempo por lentidão do ambiente, não por bug.
    testTimeout: 15000,
  },
}))
