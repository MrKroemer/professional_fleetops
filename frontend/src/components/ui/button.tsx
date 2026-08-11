import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'
import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

const variantes = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[var(--radius-base)] text-sm font-medium ' +
    'transition-colors duration-150 disabled:pointer-events-none disabled:opacity-50 ' +
    '[&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variante: {
        primaria: 'bg-marca text-marca-contraste hover:bg-marca-forte shadow-sm',
        secundaria: 'bg-superficie text-texto border border-borda-forte hover:bg-fundo-alternativo',
        sutil: 'text-texto-suave hover:bg-fundo-alternativo hover:text-texto',
        destrutiva: 'bg-critico text-white hover:opacity-90 shadow-sm',
        vinculo: 'text-marca underline-offset-4 hover:underline',
      },
      tamanho: {
        pequeno: 'h-8 px-3 text-xs',
        medio: 'h-9 px-4',
        grande: 'h-11 px-6 text-base',
        icone: 'size-9 p-0',
      },
    },
    defaultVariants: {
      variante: 'primaria',
      tamanho: 'medio',
    },
  },
)

interface ButtonProps extends ComponentProps<'button'>, VariantProps<typeof variantes> {
  /** Renderiza o filho como elemento raiz — útil para transformar links em botões. */
  asChild?: boolean
  /** Exibe um indicador de progresso e desabilita o botão. */
  carregando?: boolean
}

export function Button({
  className,
  variante,
  tamanho,
  asChild = false,
  carregando = false,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const Componente = asChild ? Slot : 'button'
  return (
    <Componente
      className={cn(variantes({ variante, tamanho }), className)}
      disabled={disabled ?? carregando}
      aria-busy={carregando || undefined}
      {...props}
    >
      {carregando ? (
        <>
          <Loader2 className="animate-spin" aria-hidden="true" />
          {children}
        </>
      ) : (
        children
      )}
    </Componente>
  )
}
