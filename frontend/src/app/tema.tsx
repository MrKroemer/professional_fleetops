import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

/**
 * Tema claro/escuro.
 *
 * A preferência fica em `localStorage` — trata-se de uma escolha de exibição, não
 * de dado sensível, ao contrário dos tokens de sessão. `sistema` acompanha a
 * preferência do sistema operacional em tempo real.
 */

export type Tema = 'claro' | 'escuro' | 'sistema'

const CHAVE = 'fleetops.tema'

interface ContextoDeTema {
  tema: Tema
  temaEfetivo: 'claro' | 'escuro'
  definirTema: (tema: Tema) => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const ContextoDeTema = createContext<ContextoDeTema | null>(null)

function lerPreferencia(): Tema {
  try {
    const salvo = localStorage.getItem(CHAVE)
    if (salvo === 'claro' || salvo === 'escuro' || salvo === 'sistema') return salvo
  } catch {
    /* localStorage indisponível (navegação privada, política corporativa) */
  }
  return 'sistema'
}

function sistemaPrefereEscuro(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function ProvedorDeTema({ children }: { children: ReactNode }) {
  const [tema, definirTemaInterno] = useState<Tema>(lerPreferencia)
  const [preferenciaDoSistema, definirPreferenciaDoSistema] = useState<'claro' | 'escuro'>(() =>
    sistemaPrefereEscuro() ? 'escuro' : 'claro',
  )

  useEffect(() => {
    const consulta = window.matchMedia('(prefers-color-scheme: dark)')
    const aoMudar = (evento: MediaQueryListEvent) => {
      definirPreferenciaDoSistema(evento.matches ? 'escuro' : 'claro')
    }
    consulta.addEventListener('change', aoMudar)
    return () => {
      consulta.removeEventListener('change', aoMudar)
    }
  }, [])

  const temaEfetivo: 'claro' | 'escuro' = tema === 'sistema' ? preferenciaDoSistema : tema

  useEffect(() => {
    document.documentElement.classList.toggle('dark', temaEfetivo === 'escuro')
    document.documentElement.style.colorScheme = temaEfetivo === 'escuro' ? 'dark' : 'light'
  }, [temaEfetivo])

  const definirTema = useCallback((novo: Tema) => {
    definirTemaInterno(novo)
    try {
      localStorage.setItem(CHAVE, novo)
    } catch {
      /* Sem persistência: o tema vale apenas para esta aba. */
    }
  }, [])

  const valor = useMemo(
    () => ({ tema, temaEfetivo, definirTema }),
    [tema, temaEfetivo, definirTema],
  )

  return <ContextoDeTema.Provider value={valor}>{children}</ContextoDeTema.Provider>
}
