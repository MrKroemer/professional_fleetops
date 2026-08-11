import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * Configuração do Vite.
 *
 * O proxy de `/api` é essencial e não é mera conveniência: o refresh token é um
 * cookie `SameSite=Strict`, que o browser só envia quando frontend e API estão na
 * mesma origem. Em desenvolvimento, isso é obtido pelo proxy abaixo; em produção,
 * pelo Nginx que serve os estáticos e encaminha `/api`.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
      '/v3/api-docs': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        /*
         * Pedaços separados por biblioteca, e não por rota.
         *
         * Cada grupo abaixo muda em ritmo próprio: o React quase nunca, os gráficos
         * só quando o painel muda, o código do produto todo dia. Separá-los faz o
         * navegador reaproveitar do cache tudo que não mudou entre duas versões — o
         * contrário de um único pacote que é rebaixado por inteiro a cada correção.
         */
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          graficos: ['recharts'],
          dados: ['@tanstack/react-query', '@tanstack/react-table'],
          formularios: ['react-hook-form', '@hookform/resolvers', 'zod'],
          interface: [
            '@radix-ui/react-dialog',
            '@radix-ui/react-dropdown-menu',
            '@radix-ui/react-tooltip',
            '@radix-ui/react-avatar',
            '@radix-ui/react-label',
            '@radix-ui/react-separator',
            'lucide-react',
          ],
        },
      },
    },
  },
})
