import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { BrowserRouter } from 'react-router-dom'

import { ProvedorDeTema } from './tema'
import { ProvedorDeTooltip } from '@/components/ui/tooltip'
import { ProvedorDeAutenticacao } from '@/features/auth/autenticacao'
import { ErroDaApi } from '@/lib/api/problem'

/**
 * Provedores globais da aplicação.
 *
 * A ordem importa: o roteador precisa envolver a autenticação (que redireciona),
 * e o cliente de consultas precisa envolver a autenticação (que limpa o cache ao
 * encerrar a sessão).
 */
export function Provedores({ children }: { children: ReactNode }) {
  const [clienteDeConsultas] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: (tentativas, erro) => {
              // Erros de permissão, validação e "não encontrado" não melhoram com
              // repetição; só falhas de rede e de servidor valem uma nova tentativa.
              if (erro instanceof ErroDaApi && !erro.eRecuperavel) return false
              return tentativas < 2
            },
          },
          mutations: { retry: false },
        },
      }),
  )

  return (
    <QueryClientProvider client={clienteDeConsultas}>
      <ProvedorDeTema>
        <ProvedorDeTooltip delayDuration={300}>
          <BrowserRouter>
            <ProvedorDeAutenticacao>{children}</ProvedorDeAutenticacao>
          </BrowserRouter>
        </ProvedorDeTooltip>
      </ProvedorDeTema>
    </QueryClientProvider>
  )
}
