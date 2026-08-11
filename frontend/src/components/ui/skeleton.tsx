import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

/**
 * Espaço reservado durante o carregamento.
 *
 * É marcado com `aria-hidden` porque o anúncio do carregamento a leitores de tela
 * é feito pela região viva do container, não repetido em cada bloco cinza.
 */
export function Skeleton({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-pulse rounded-[var(--radius-base)] bg-fundo-alternativo', className)}
      {...props}
    />
  )
}

/** Esqueleto de tabela: reproduz a silhueta da lista que está sendo carregada. */
export function SkeletonDeTabela({ linhas = 5, colunas = 4 }: { linhas?: number; colunas?: number }) {
  return (
    <div className="space-y-2" role="status" aria-live="polite" aria-label="Carregando dados">
      <Skeleton className="h-9 w-full" />
      {Array.from({ length: linhas }).map((_, indiceLinha) => (
        <div key={indiceLinha} className="flex gap-3">
          {Array.from({ length: colunas }).map((_, indiceColuna) => (
            <Skeleton
              key={indiceColuna}
              className={cn('h-11 flex-1', indiceColuna === 0 && 'max-w-[38%]')}
            />
          ))}
        </div>
      ))}
      <span className="sr-only">Carregando dados…</span>
    </div>
  )
}
