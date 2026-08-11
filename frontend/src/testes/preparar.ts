import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

/**
 * Preparação comum dos testes.
 *
 * Desmonta a árvore entre os testes e fornece as APIs de browser que o jsdom não
 * implementa e das quais o Radix depende — sem elas, qualquer componente com menu,
 * diálogo ou tooltip quebra no ambiente de teste por motivo alheio ao que se quer verificar.
 */

afterEach(() => {
  cleanup()
})

if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((consulta: string) => ({
    matches: false,
    media: consulta,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {
      /* sem efeito no jsdom */
    }
    unobserve() {
      /* sem efeito no jsdom */
    }
    disconnect() {
      /* sem efeito no jsdom */
    }
  }
}

// O Radix usa estas APIs de ponteiro para posicionar menus e diálogos.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {
    /* sem efeito no jsdom */
  }
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {
    /* sem efeito no jsdom */
  }
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {
    /* sem efeito no jsdom */
  }
}
