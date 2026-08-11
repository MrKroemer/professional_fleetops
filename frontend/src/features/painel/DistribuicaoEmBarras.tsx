import { useId } from 'react'

import { Card } from '@/components/ui/card'
import { formatarNumero } from '@/lib/formatters'
import { cn } from '@/lib/utils'

export interface FatiaDeDistribuicao {
  chave: string
  rotulo: string
  quantidade: number
}

/**
 * Distribuição de uma grandeza entre classes nominais, em barras horizontais.
 *
 * <p>Escolhas deliberadas, e o motivo de cada uma:
 *
 * - **Barra horizontal, não pizza.** A tarefa do leitor é comparar magnitudes; o
 *   comprimento resolve isso melhor que o ângulo, e rótulos longos como "Posto de
 *   combustível" cabem sem girar texto.
 * - **Uma cor só, não uma por classe.** Estas classes são nominais — locadora, UF,
 *   categoria. Colorir cada uma gastaria o canal de identidade para recodificar o que
 *   o comprimento da barra já mostra, e criaria um arco-íris que nada significa.
 * - **Valor rotulado em cada linha.** Dispensa o leitor de estimar contra um eixo, e
 *   é a contrapartida exigida quando a marca fica perto do piso de contraste.
 * - **HTML e CSS, não SVG.** São linhas de medidor, não um gráfico com eixos: o
 *   navegador já sabe alinhar, truncar e tornar acessível uma lista.
 */
export function DistribuicaoEmBarras({
  titulo,
  descricao,
  fatias,
  unidade = 'registro',
  limite,
  className,
}: {
  titulo: string
  descricao?: string
  fatias: FatiaDeDistribuicao[]
  /** Palavra usada na leitura acessível de cada linha, no singular. */
  unidade?: string
  /** Mostra apenas as N maiores classes, agrupando o restante. */
  limite?: number
  className?: string
}) {
  const idDoTitulo = useId()
  const total = fatias.reduce((soma, fatia) => soma + fatia.quantidade, 0)
  const maior = fatias.reduce((maximo, fatia) => Math.max(maximo, fatia.quantidade), 0)

  // Acima do limite, a cauda vira uma linha "Outros" — mais classes do que o olho
  // compara de uma vez viram ruído, e a soma continua correta.
  const visiveis = limite && fatias.length > limite ? fatias.slice(0, limite) : fatias
  const cauda = limite && fatias.length > limite ? fatias.slice(limite) : []
  const linhas =
    cauda.length > 0
      ? [
          ...visiveis,
          {
            chave: '__outros__',
            rotulo: `Outros (${String(cauda.length)})`,
            quantidade: cauda.reduce((soma, fatia) => soma + fatia.quantidade, 0),
          },
        ]
      : visiveis

  return (
    <Card className={cn('p-5', className)}>
      <div className="mb-4">
        <h3 id={idDoTitulo} className="text-sm font-semibold text-texto">
          {titulo}
        </h3>
        {descricao ? <p className="mt-0.5 text-xs text-texto-suave">{descricao}</p> : null}
      </div>

      {linhas.length === 0 ? (
        <p className="py-6 text-center text-sm text-texto-tenue">Sem dados para exibir.</p>
      ) : (
        <ul aria-labelledby={idDoTitulo} className="space-y-2.5">
          {linhas.map((fatia, indice) => {
            const proporcao = maior > 0 ? fatia.quantidade / maior : 0
            const participacao = total > 0 ? (fatia.quantidade / total) * 100 : 0
            return (
              <li
                key={fatia.chave}
                className="surgir group"
                style={{ '--indice': indice } as React.CSSProperties}
                title={`${fatia.rotulo}: ${formatarNumero(fatia.quantidade)} de ${formatarNumero(total)} (${participacao.toFixed(1).replace('.', ',')}%)`}
              >
                <div className="mb-1 flex items-baseline justify-between gap-3">
                  <span className="min-w-0 truncate text-sm text-texto">{fatia.rotulo}</span>
                  <span className="shrink-0 text-sm font-medium tabular-nums text-texto">
                    {formatarNumero(fatia.quantidade)}
                    <span className="ml-1.5 text-xs font-normal text-texto-tenue">
                      {participacao.toFixed(0)}%
                    </span>
                  </span>
                </div>
                {/* O trilho dá o contexto do máximo; a marca cresce ao montar. */}
                <div
                  className="h-2 w-full overflow-hidden rounded-full bg-fundo-alternativo"
                  role="img"
                  aria-label={`${fatia.rotulo}: ${formatarNumero(fatia.quantidade)} ${unidade}${fatia.quantidade === 1 ? '' : 's'}, ${participacao.toFixed(0)} por cento do total`}
                >
                  <span
                    className={cn(
                      'block h-full rounded-full bg-marca transition-[width] duration-700 ease-out',
                      'group-hover:bg-marca-intensa',
                    )}
                    style={{ width: `${String(Math.max(proporcao * 100, fatia.quantidade > 0 ? 2 : 0))}%` }}
                  />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}
