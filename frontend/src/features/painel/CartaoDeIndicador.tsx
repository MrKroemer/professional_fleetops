import type { LucideIcon } from 'lucide-react'
import type { MouseEvent, ReactNode } from 'react'

import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * Cartão de indicador do painel.
 *
 * <p>É um número, não um gráfico: um valor isolado com um qualificador é lido mais
 * rápido como texto grande do que como uma barra solitária.
 *
 * <p>O brilho que acompanha o cursor vem de `pages_fleet/street.html`, reduzido ao que
 * cabe em um painel denso: um halo suave na cor da marca, sem a revelação deslizante do
 * original — em uma grade de seis cartões, seis animações competindo cansariam mais do
 * que informariam.
 */
export function CartaoDeIndicador({
  rotulo,
  valor,
  detalhe,
  icone: Icone,
  destaque = false,
  aviso,
  indice = 0,
  className,
}: {
  rotulo: string
  valor: ReactNode
  detalhe?: ReactNode
  icone: LucideIcon
  /** Aumenta o valor e reserva a cor de marca — use em um cartão por painel. */
  destaque?: boolean
  /** Nota de rodapé, para quando o número é uma estimativa e não um fato apurado. */
  aviso?: string
  indice?: number
  className?: string
}) {
  const acompanharCursor = (evento: MouseEvent<HTMLDivElement>) => {
    const area = evento.currentTarget.getBoundingClientRect()
    evento.currentTarget.style.setProperty(
      '--cursor-x',
      `${String(((evento.clientX - area.left) / area.width) * 100)}%`,
    )
    evento.currentTarget.style.setProperty(
      '--cursor-y',
      `${String(((evento.clientY - area.top) / area.height) * 100)}%`,
    )
  }

  return (
    <Card
      onMouseMove={acompanharCursor}
      style={{ '--indice': indice } as React.CSSProperties}
      className={cn(
        'brilho-de-marca surgir relative overflow-hidden p-4 transition-all duration-300',
        'hover:-translate-y-0.5 hover:border-borda-forte hover:shadow-lg',
        className,
      )}
    >
      <div className="relative z-10 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-texto-tenue">{rotulo}</p>
          <p
            className={cn(
              'mt-1.5 font-semibold tabular-nums tracking-tight',
              destaque ? 'text-3xl text-marca-forte' : 'text-2xl text-texto',
            )}
          >
            {valor}
          </p>
          {detalhe ? <p className="mt-1 text-xs text-texto-suave">{detalhe}</p> : null}
        </div>
        <span
          className={cn(
            'grid size-9 shrink-0 place-items-center rounded-[var(--radius-base)] transition-colors',
            destaque ? 'bg-marca text-marca-contraste' : 'bg-marca-suave text-marca-forte',
          )}
          aria-hidden="true"
        >
          <Icone className="size-4" />
        </span>
      </div>
      {aviso ? (
        <p className="relative z-10 mt-3 border-t border-borda pt-2 text-[0.68rem] leading-snug text-texto-tenue">
          {aviso}
        </p>
      ) : null}
    </Card>
  )
}
