import * as TooltipPrimitive from '@radix-ui/react-tooltip'
import type { ComponentProps, ReactNode } from 'react'

import { cn } from '@/lib/utils'

export const ProvedorDeTooltip = TooltipPrimitive.Provider

interface TooltipProps {
  conteudo: ReactNode
  children: ReactNode
  lado?: ComponentProps<typeof TooltipPrimitive.Content>['side']
}

/** Dica curta em hover ou foco. Nunca carrega informação essencial — apenas complementa. */
export function Tooltip({ conteudo, children, lado = 'right' }: TooltipProps) {
  return (
    <TooltipPrimitive.Root>
      <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          side={lado}
          sideOffset={8}
          className={cn(
            'z-50 rounded-[var(--radius-base)] bg-texto px-2.5 py-1.5 text-xs font-medium',
            'text-fundo shadow-md animate-in fade-in-0 zoom-in-95',
          )}
        >
          {conteudo}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  )
}
