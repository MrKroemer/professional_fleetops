import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

export const Dialog = DialogPrimitive.Root
export const DialogGatilho = DialogPrimitive.Trigger
export const DialogFechar = DialogPrimitive.Close

interface DialogConteudoProps extends ComponentProps<typeof DialogPrimitive.Content> {
  /** Formulários de cadastro precisam de mais largura que uma confirmação simples. */
  largura?: 'media' | 'grande'
}

export function DialogConteudo({
  className,
  children,
  largura = 'media',
  ...props
}: DialogConteudoProps) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay
        className={cn(
          'fixed inset-0 z-50 bg-black/50 backdrop-blur-[2px]',
          'data-[state=open]:animate-in data-[state=open]:fade-in-0',
          'data-[state=closed]:animate-out data-[state=closed]:fade-out-0',
        )}
      />
      <DialogPrimitive.Content
        className={cn(
          'fixed left-1/2 top-1/2 z-50 flex max-h-[92vh] w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2',
          'flex-col overflow-hidden rounded-[calc(var(--radius-base)*1.75)] border border-borda',
          'bg-superficie shadow-xl',
          largura === 'grande' ? 'max-w-3xl' : 'max-w-lg',
          'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
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

export function DialogCabecalho({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn('shrink-0 space-y-1 border-b border-borda px-6 py-4 pr-12', className)}
      {...props}
    />
  )
}

export function DialogTitulo({ className, ...props }: ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      className={cn('text-base font-semibold leading-tight text-texto', className)}
      {...props}
    />
  )
}

export function DialogDescricao({
  className,
  ...props
}: ComponentProps<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn('text-sm text-texto-suave', className)} {...props} />
}

/** Área rolável do diálogo: o cabeçalho e o rodapé permanecem fixos. */
export function DialogCorpo({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('min-h-0 flex-1 overflow-y-auto px-6 py-5', className)} {...props} />
}

export function DialogRodape({ className, ...props }: ComponentProps<'div'>) {
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
