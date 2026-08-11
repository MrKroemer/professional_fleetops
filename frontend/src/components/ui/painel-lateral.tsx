import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import type { ComponentProps, ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * Painel deslizante lateral, para o detalhe de um registro.
 *
 * Escolhido em vez de um diálogo centralizado porque o detalhe é consultado **junto**
 * da lista: o painel entra pela direita e deixa a tabela visível atrás, de modo que o
 * usuário mantenha o contexto de onde estava. Um modal centralizado apagaria essa
 * referência a cada abertura.
 */
export const PainelLateral = DialogPrimitive.Root
export const PainelLateralGatilho = DialogPrimitive.Trigger

export function PainelLateralConteudo({ className, children, ...props }: ComponentProps<typeof DialogPrimitive.Content>) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay
        className={cn(
          'fixed inset-0 z-50 bg-black/40 backdrop-blur-[2px]',
          'data-[state=open]:animate-in data-[state=open]:fade-in-0',
          'data-[state=closed]:animate-out data-[state=closed]:fade-out-0',
        )}
      />
      <DialogPrimitive.Content
        className={cn(
          'fixed inset-y-0 right-0 z-50 flex w-[calc(100vw-2rem)] max-w-xl flex-col',
          'border-l border-borda bg-superficie shadow-2xl',
          'data-[state=open]:animate-in data-[state=open]:slide-in-from-right',
          'data-[state=closed]:animate-out data-[state=closed]:slide-out-to-right',
          'duration-300',
          className,
        )}
        {...props}
      >
        {children}
        <DialogPrimitive.Close
          className={cn(
            'absolute right-3 top-3 rounded-[var(--radius-base)] p-1.5 text-texto-tenue transition-colors',
            'hover:bg-fundo-alternativo hover:text-texto',
          )}
        >
          <X className="size-4" aria-hidden="true" />
          <span className="sr-only">Fechar</span>
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}

export function PainelLateralCabecalho({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div className={cn('shrink-0 border-b border-borda px-6 py-4 pr-12', className)} {...props} />
  )
}

export function PainelLateralTitulo({ className, ...props }: ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      className={cn('text-lg font-semibold leading-tight text-texto', className)}
      {...props}
    />
  )
}

export function PainelLateralDescricao({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Description>) {
  return (
    <DialogPrimitive.Description className={cn('mt-0.5 text-sm text-texto-suave', className)} {...props} />
  )
}

export function PainelLateralCorpo({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('min-h-0 flex-1 overflow-y-auto px-6 py-5', className)} {...props} />
}

export function PainelLateralRodape({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn(
        'flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-borda',
        'bg-fundo-alternativo/50 px-6 py-3',
        className,
      )}
      {...props}
    />
  )
}

/**
 * Um bloco de campos rotulados.
 *
 * Usa `<dl>` porque é literalmente uma lista de definições: leitores de tela anunciam
 * a associação entre rótulo e valor, o que uma grade de `<div>` não faria.
 */
export function ListaDeCampos({
  titulo,
  children,
  colunas = 2,
}: {
  titulo?: string
  children: ReactNode
  colunas?: 1 | 2
}) {
  return (
    <section className="mb-6 last:mb-0">
      {titulo ? (
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-texto-tenue">
          {titulo}
        </h3>
      ) : null}
      <dl className={cn('grid gap-x-4 gap-y-3', colunas === 2 ? 'sm:grid-cols-2' : 'grid-cols-1')}>
        {children}
      </dl>
    </section>
  )
}

export function Campo({
  rotulo,
  children,
  larguraTotal = false,
}: {
  rotulo: string
  children: ReactNode
  larguraTotal?: boolean
}) {
  return (
    <div className={cn('min-w-0', larguraTotal && 'sm:col-span-2')}>
      <dt className="text-xs text-texto-tenue">{rotulo}</dt>
      <dd className="mt-0.5 break-words text-sm text-texto">{children}</dd>
    </div>
  )
}
