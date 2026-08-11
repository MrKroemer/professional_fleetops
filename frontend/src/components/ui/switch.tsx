import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

interface SwitchProps extends Omit<ComponentProps<'input'>, 'type'> {
  rotulo: string
  descricao?: string
}

/**
 * Interruptor de duas posições.
 *
 * Construído sobre um `<input type="checkbox">` real, e não sobre uma `<div>` com
 * `role="switch"`: assim o campo participa naturalmente do formulário, do
 * `react-hook-form` e da navegação por teclado, sem precisar reimplementar nada disso.
 */
export function Switch({ className, rotulo, descricao, id, ...props }: SwitchProps) {
  return (
    <label
      htmlFor={id}
      className={cn('flex cursor-pointer items-start gap-3 select-none', className)}
    >
      <span className="relative mt-0.5 inline-flex shrink-0">
        <input id={id} type="checkbox" className="peer sr-only" {...props} />
        <span
          aria-hidden="true"
          className={cn(
            'block h-5 w-9 rounded-full bg-borda-forte transition-colors',
            'peer-checked:bg-marca peer-disabled:opacity-50',
            'peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2',
            'peer-focus-visible:outline-anel',
          )}
        />
        <span
          aria-hidden="true"
          className={cn(
            'pointer-events-none absolute left-0.5 top-0.5 size-4 rounded-full bg-white shadow',
            'transition-transform peer-checked:translate-x-4',
          )}
        />
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-texto">{rotulo}</span>
        {descricao ? <span className="block text-xs text-texto-suave">{descricao}</span> : null}
      </span>
    </label>
  )
}
