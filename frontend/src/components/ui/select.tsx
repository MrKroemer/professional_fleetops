import { ChevronDown } from 'lucide-react'
import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

interface SelectProps extends ComponentProps<'select'> {
  invalido?: boolean
}

/**
 * Campo de seleção nativo.
 *
 * Deliberadamente nativo, e não uma listbox do Radix: em telas densas de back-office o
 * `<select>` do sistema é mais rápido de operar por teclado, funciona no mobile com o
 * seletor nativo e não custa nada em JavaScript. Componentes ricos ficam reservados
 * para casos que exigem busca ou seleção múltipla.
 */
export function Select({ className, invalido, children, ...props }: SelectProps) {
  return (
    <div className="relative">
      <select
        aria-invalid={invalido || undefined}
        className={cn(
          'h-9 w-full appearance-none rounded-[var(--radius-base)] border border-borda-forte bg-superficie',
          'px-3 pr-8 text-sm text-texto transition-colors',
          'disabled:cursor-not-allowed disabled:opacity-60',
          'aria-[invalid=true]:border-critico',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown
        className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-texto-tenue"
        aria-hidden="true"
      />
    </div>
  )
}
