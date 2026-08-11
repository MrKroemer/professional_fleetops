import type { ComponentProps } from 'react'

import { cn } from '@/lib/utils'

interface TextareaProps extends ComponentProps<'textarea'> {
  invalido?: boolean
}

export function Textarea({ className, invalido, ...props }: TextareaProps) {
  return (
    <textarea
      aria-invalid={invalido || undefined}
      className={cn(
        'flex min-h-20 w-full rounded-[var(--radius-base)] border border-borda-forte bg-superficie px-3 py-2',
        'text-sm text-texto placeholder:text-texto-tenue transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-60',
        'aria-[invalid=true]:border-critico',
        className,
      )}
      {...props}
    />
  )
}
