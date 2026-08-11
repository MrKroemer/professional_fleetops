import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

const variantes = cva(
  'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap',
  {
    variants: {
      variante: {
        neutra: 'border-borda-forte bg-fundo-alternativo text-texto-suave',
        marca: 'border-transparent bg-marca-suave text-marca-forte',
        critica: 'border-transparent bg-critico-suave text-critico',
        atencao: 'border-transparent bg-atencao-suave text-atencao',
        sucesso: 'border-transparent bg-sucesso-suave text-sucesso',
        informativa: 'border-transparent bg-informativo-suave text-informativo',
      },
    },
    defaultVariants: { variante: 'neutra' },
  },
)

interface BadgeProps extends ComponentProps<'span'>, VariantProps<typeof variantes> {}

export function Badge({ className, variante, ...props }: BadgeProps) {
  return <span className={cn(variantes({ variante }), className)} {...props} />
}
