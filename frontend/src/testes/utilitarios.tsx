import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'

import { ProvedorDeTooltip } from '@/components/ui/tooltip'
import { ContextoDeAutenticacao } from '@/features/auth/autenticacao'
import type { Perfil, Usuario } from '@/features/auth/tipos'

/**
 * Utilitários de renderização dos testes.
 *
 * Monta apenas os provedores de que as telas realmente dependem, com um cliente de
 * consultas novo a cada teste — cache compartilhado entre casos produz falhas que
 * dependem da ordem de execução e são difíceis de reproduzir.
 */

export function usuarioDeTeste(perfil: Perfil = 'GESTOR_FROTA'): Usuario {
  return {
    id: 1,
    nome: 'Ana Souza',
    email: 'ana@proyfebrasil.com.br',
    perfil,
    perfilDescricao: perfil === 'ADMIN' ? 'Administrador' : 'Gestor de frota',
    ativo: true,
    criadoEm: '2026-01-01T00:00:00Z',
  }
}

interface OpcoesDeRenderizacao extends Omit<RenderOptions, 'wrapper'> {
  /** Perfil do usuário autenticado durante o teste (RN-19). */
  perfil?: Perfil
  /** Rota inicial do roteador em memória. */
  rota?: string
}

export function renderizarComProvedores(
  elemento: ReactElement,
  { perfil = 'GESTOR_FROTA', rota = '/', ...opcoes }: OpcoesDeRenderizacao = {},
) {
  const clienteDeConsultas = new QueryClient({
    defaultOptions: {
      // Sem repetição: um teste que espera erro não deve esperar três tentativas.
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })

  const usuario = usuarioDeTeste(perfil)
  const sessao = {
    estado: 'autenticado' as const,
    usuario,
    entrar: () => Promise.resolve(),
    sair: () => Promise.resolve(),
    temPerfil: (...perfis: Perfil[]) => perfis.includes(perfil),
  }

  function Provedores({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={clienteDeConsultas}>
        <ProvedorDeTooltip>
          <MemoryRouter initialEntries={[rota]}>
            <ContextoDeAutenticacao.Provider value={sessao}>{children}</ContextoDeAutenticacao.Provider>
          </MemoryRouter>
        </ProvedorDeTooltip>
      </QueryClientProvider>
    )
  }

  return render(elemento, { wrapper: Provedores, ...opcoes })
}
