import type { ReactNode } from 'react'

import { Label } from './label'
import { cn } from '@/lib/utils'

interface CampoDeFormularioProps {
  id: string
  rotulo: string
  erro?: string | undefined
  dica?: string | undefined
  obrigatorio?: boolean
  className?: string
  children: ReactNode
}

/**
 * Rótulo, campo, dica e mensagem de erro em um bloco coeso.
 *
 * Concentra a ligação de acessibilidade: o erro é anunciado por `aria-describedby`
 * e por uma região viva, de modo que quem usa leitor de tela ouve a rejeição do
 * campo sem precisar navegar até ela.
 */
export function CampoDeFormulario({
  id,
  rotulo,
  erro,
  dica,
  obrigatorio,
  className,
  children,
}: CampoDeFormularioProps) {
  const idDica = dica ? `${id}-dica` : undefined
  const idErro = erro ? `${id}-erro` : undefined

  return (
    <div className={cn('space-y-1.5', className)}>
      <Label htmlFor={id}>
        {rotulo}
        {obrigatorio ? (
          <span className="ml-0.5 text-critico" aria-hidden="true">
            *
          </span>
        ) : null}
      </Label>
      {children}
      {dica && !erro ? (
        <p id={idDica} className="text-xs text-texto-tenue">
          {dica}
        </p>
      ) : null}
      {erro ? (
        <p id={idErro} role="alert" className="text-xs font-medium text-critico">
          {erro}
        </p>
      ) : null}
    </div>
  )
}

