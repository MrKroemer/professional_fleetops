import type { ReactNode } from 'react'

import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * Moldura comum dos gráficos: título, leitura e a área do desenho.
 *
 * A "leitura" não é um subtítulo decorativo — é a frase que diz o que o gráfico revela.
 * Um gráfico que obriga o leitor a descobrir sozinho qual é a conclusão desperdiça a
 * chance de informar, e é o erro mais comum em painel corporativo.
 */
export function MolduraDeGrafico({
  titulo,
  leitura,
  legenda,
  acao,
  children,
  className,
  alturaDoDesenho = 260,
}: {
  titulo: string
  leitura: string
  /** Legenda de séries; obrigatória a partir de duas, para que identidade não seja só cor. */
  legenda?: ReactNode
  acao?: ReactNode
  children: ReactNode
  className?: string
  alturaDoDesenho?: number
}) {
  return (
    <Card className={cn('flex flex-col p-5', className)}>
      <header className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-texto">{titulo}</h3>
          <p className="mt-0.5 max-w-prose text-xs leading-relaxed text-texto-suave">{leitura}</p>
        </div>
        {acao}
      </header>

      {legenda ? <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-1.5">{legenda}</div> : null}

      {/*
        Altura fixa, sem `flex-1`: em uma coluna flex, `flex: 1 1 0%` zera a base e
        anula o `height` do eixo principal. A área do desenho então só ganhava altura
        quando um card vizinho da mesma linha da grade a esticava — foi o que fez a
        rosca e as barras sumirem enquanto o gráfico de linhas aparecia.
      */}
      <div className="min-w-0 shrink-0" style={{ height: alturaDoDesenho }}>
        {children}
      </div>
    </Card>
  )
}

/** Um item de legenda: marca colorida seguida do rótulo em tinta de texto. */
export function ItemDeLegenda({ cor, rotulo, valor }: { cor: string; rotulo: string; valor?: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs">
      <span
        aria-hidden="true"
        className="size-2.5 shrink-0 rounded-[3px]"
        style={{ backgroundColor: cor }}
      />
      <span className="text-texto-suave">{rotulo}</span>
      {valor ? <span className="font-medium tabular-nums text-texto">{valor}</span> : null}
    </span>
  )
}

/**
 * Dica flutuante dos gráficos.
 *
 * Escrita à mão em vez de usar a padrão do Recharts porque a padrão herda cores
 * inline que ignoram o tema — no escuro ela aparece branca sobre branco.
 */
export function DicaDeGrafico({
  titulo,
  linhas,
}: {
  titulo: string
  linhas: { cor?: string; rotulo: string; valor: string }[]
}) {
  return (
    <div className="pointer-events-none rounded-[var(--radius-base)] border border-borda bg-superficie-elevada px-3 py-2 shadow-lg">
      <p className="mb-1 text-xs font-semibold text-texto">{titulo}</p>
      <ul className="space-y-0.5">
        {linhas.map((linha) => (
          <li key={linha.rotulo} className="flex items-center gap-2 text-xs">
            {linha.cor ? (
              <span
                aria-hidden="true"
                className="size-2 shrink-0 rounded-[2px]"
                style={{ backgroundColor: linha.cor }}
              />
            ) : null}
            <span className="text-texto-suave">{linha.rotulo}</span>
            <span className="ml-auto font-medium tabular-nums text-texto">{linha.valor}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
