import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

export function Card({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn(
        'rounded-[calc(var(--radius-base)*1.5)] border border-borda bg-superficie shadow-sm',
        className,
      )}
      {...props}
    />
  )
}

export function CardCabecalho({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex flex-col gap-1 p-5 pb-3', className)} {...props} />
}

export function CardTitulo({ className, ...props }: ComponentProps<'h3'>) {
  return (
    <h3 className={cn('text-base font-semibold leading-tight text-texto', className)} {...props} />
  )
}

export function CardDescricao({ className, ...props }: ComponentProps<'p'>) {
  return <p className={cn('text-sm text-texto-suave', className)} {...props} />
}

export function CardConteudo({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('p-5 pt-0', className)} {...props} />
}

export function CardRodape({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex items-center gap-2 p-5 pt-0', className)} {...props} />
}
