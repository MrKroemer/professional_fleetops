import type { ComponentProps, ReactNode } from 'react'

import { Card } from './card'
import { cn } from '@/lib/utils'

/**
 * Tabela de dados do sistema.
 *
 * Concentra decisões que valem para todas as listagens: números tabulares para que
 * colunas de KM e valores alinhem, rolagem horizontal contida no próprio card (a página
 * nunca rola na horizontal) e legenda acessível obrigatória.
 */

interface TabelaProps extends ComponentProps<'table'> {
  /** Descrição da tabela para leitores de tela. Obrigatória. */
  legenda: string
  /** Conteúdo exibido abaixo da tabela, tipicamente a contagem de registros. */
  rodape?: ReactNode
}

export function Tabela({ legenda, rodape, className, children, ...props }: TabelaProps) {
  return (
    <Card className="overflow-hidden">
      <div className="overflow-x-auto">
        <table className={cn('w-full text-sm', className)} {...props}>
          <caption className="sr-only">{legenda}</caption>
          {children}
        </table>
      </div>
      {rodape ? (
        <div className="border-t border-borda px-4 py-2.5 text-xs text-texto-tenue">{rodape}</div>
      ) : null}
    </Card>
  )
}

export function TabelaCabecalho({ className, ...props }: ComponentProps<'thead'>) {
  return <thead className={cn('border-b border-borda bg-fundo-alternativo/60', className)} {...props} />
}

export function TabelaCorpo({ className, ...props }: ComponentProps<'tbody'>) {
  return <tbody className={className} {...props} />
}

export function TabelaLinha({ className, ...props }: ComponentProps<'tr'>) {
  return (
    <tr
      className={cn('border-b border-borda transition-colors last:border-0 hover:bg-fundo-alternativo/40', className)}
      {...props}
    />
  )
}

interface TabelaColunaProps extends ComponentProps<'th'> {
  /** Alinha à direita — use em colunas numéricas e monetárias. */
  numerica?: boolean
}

export function TabelaColuna({ className, numerica, ...props }: TabelaColunaProps) {
  return (
    <th
      scope="col"
      className={cn(
        'whitespace-nowrap px-4 py-2.5 text-left font-medium text-texto-suave',
        numerica && 'text-right',
        className,
      )}
      {...props}
    />
  )
}

interface TabelaCelulaProps extends ComponentProps<'td'> {
  numerica?: boolean
}

export function TabelaCelula({ className, numerica, ...props }: TabelaCelulaProps) {
  return <td className={cn('px-4 py-3 align-middle', numerica && 'text-right', className)} {...props} />
}

/** Célula de ações, encostada à direita e sem quebra de linha. */
export function TabelaAcoes({ className, ...props }: ComponentProps<'td'>) {
  return (
    <td className={cn('whitespace-nowrap px-4 py-3 text-right', className)}>
      <div className="flex items-center justify-end gap-1" {...props} />
    </td>
  )
}
