import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

interface InputProps extends ComponentProps<'input'> {
  /** Marca visualmente o campo como inválido e anuncia o estado a leitores de tela. */
  invalido?: boolean
}

export function Input({ className, invalido, ...props }: InputProps) {
  return (
    <input
      aria-invalid={invalido || undefined}
      className={cn(
        'flex h-9 w-full rounded-[var(--radius-base)] border border-borda-forte bg-superficie px-3 py-1 text-sm',
        'text-texto placeholder:text-texto-tenue transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-60',
        'aria-[invalid=true]:border-critico',
        className,
      )}
      {...props}
    />
  )
}
