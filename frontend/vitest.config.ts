import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

/**
 * Configuração dos testes do frontend.
 *
 * Separada de `vite.config.ts` de propósito: o build de produção não precisa carregar
 * o plugin do Tailwind nem o ambiente de DOM, e misturar as duas configurações
 * tornaria mais difícil enxergar o que vale para cada caso.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  test: {
    environment: 'jsdom',
    // Sem globais: cada teste importa `describe`/`it`/`expect` explicitamente. Evita
    // depender de tipos ambientes e deixa claro de onde vem cada função.
    globals: false,
    setupFiles: ['./src/testes/preparar.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'html'],
      include: ['src/features/**', 'src/lib/**'],
      exclude: ['src/lib/api/schema.d.ts', '**/*.test.{ts,tsx}'],
    },
  },
})
