import { AlertTriangle, Inbox, RotateCw, ShieldAlert } from 'lucide-react'
import type { ReactNode } from 'react'

import { Button } from './button'
import { ErroDaApi, mensagemDeErro } from '@/lib/api/problem'
import { cn } from '@/lib/utils'

/**
 * Os três estados obrigatórios de toda tela — vazio e erro; o de carregamento é
 * coberto por `Skeleton`.
 *
 * Ficam centralizados aqui para que nenhuma tela invente sua própria mensagem de
 * lista vazia ou de falha, e para que a ação de recuperação esteja sempre presente.
 */

interface EstadoVazioProps {
  titulo: string
  descricao?: string
  icone?: ReactNode
  acao?: ReactNode
  className?: string
}

export function EstadoVazio({ titulo, descricao, icone, acao, className }: EstadoVazioProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-[calc(var(--radius-base)*1.5)]',
        'border border-dashed border-borda-forte bg-fundo-alternativo/50 px-6 py-14 text-center',
        className,
      )}
    >
      <div className="rounded-full bg-superficie p-3 text-texto-tenue shadow-sm" aria-hidden="true">
        {icone ?? <Inbox className="size-6" />}
      </div>
      <div className="space-y-1">
        <p className="text-sm font-semibold text-texto">{titulo}</p>
        {descricao ? <p className="max-w-md text-sm text-texto-suave">{descricao}</p> : null}
      </div>
      {acao}
    </div>
  )
}

interface EstadoDeErroProps {
  erro: unknown
  aoTentarNovamente?: () => void
  className?: string
}

export function EstadoDeErro({ erro, aoTentarNovamente, className }: EstadoDeErroProps) {
  const acessoNegado = erro instanceof ErroDaApi && erro.eAcessoNegado
  const requestId = erro instanceof ErroDaApi ? erro.requestId : undefined

  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-[calc(var(--radius-base)*1.5)]',
        'border border-critico/30 bg-critico-suave/40 px-6 py-12 text-center',
        className,
      )}
    >
      <div className="rounded-full bg-superficie p-3 text-critico shadow-sm" aria-hidden="true">
        {acessoNegado ? <ShieldAlert className="size-6" /> : <AlertTriangle className="size-6" />}
      </div>
      <div className="space-y-1">
        <p className="text-sm font-semibold text-texto">
          {acessoNegado ? 'Acesso negado' : 'Não foi possível carregar'}
        </p>
        <p className="max-w-md text-sm text-texto-suave">{mensagemDeErro(erro)}</p>
        {requestId ? (
          <p className="text-xs text-texto-tenue">
            Identificador da requisição: <code className="font-mono">{requestId}</code>
          </p>
        ) : null}
      </div>
      {aoTentarNovamente && !acessoNegado ? (
        <Button variante="secundaria" tamanho="pequeno" onClick={aoTentarNovamente}>
          <RotateCw aria-hidden="true" />
          Tentar novamente
        </Button>
      ) : null}
    </div>
  )
}
