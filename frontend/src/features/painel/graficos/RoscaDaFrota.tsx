import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'

import { DicaDeGrafico, ItemDeLegenda, MolduraDeGrafico } from './MolduraDeGrafico'
import { corDaSerie, dobrarCauda } from '@/lib/graficos'
import { formatarNumero } from '@/lib/formatters'

interface Fatia {
  chave: string
  rotulo: string
  quantidade: number
}

/**
 * Composição da frota por categoria, em rosca.
 *
 * A rosca — e não a pizza cheia — porque o vazio central acomoda o total, que é o
 * número que dá sentido às proporções. Fica limitada a três categorias mais "Outros":
 * é o teto validado da paleta, e também o limite prático de fatias que alguém compara
 * de relance.
 *
 * Cada fatia carrega rótulo e percentual na legenda, então a identidade nunca depende
 * só da cor.
 */
export function RoscaDaFrota({ fatias }: { fatias: Fatia[] }) {
  const dados = dobrarCauda(fatias)
  const total = dados.reduce((soma, fatia) => soma + fatia.quantidade, 0)
  const maior = dados[0]

  return (
    <MolduraDeGrafico
      titulo="Composição da frota"
      leitura={
        maior && total > 0
          ? `${maior.rotulo} responde por ${String(Math.round((maior.quantidade / total) * 100))}% dos ${formatarNumero(total)} veículos cadastrados.`
          : 'Sem veículos cadastrados.'
      }
      legenda={dados.map((fatia, indice) => (
        <ItemDeLegenda
          key={fatia.rotulo}
          cor={corDaSerie(fatia.ehOutros ? Number.MAX_SAFE_INTEGER : indice)}
          rotulo={fatia.rotulo}
          valor={`${formatarNumero(fatia.quantidade)} · ${String(Math.round((fatia.quantidade / total) * 100))}%`}
        />
      ))}
      alturaDoDesenho={240}
    >
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={dados}
            dataKey="quantidade"
            nameKey="rotulo"
            innerRadius="58%"
            outerRadius="88%"
            paddingAngle={2}
            /* Anel de superfície entre as fatias: separa marcas que se tocam. */
            stroke="var(--superficie)"
            strokeWidth={2}
            isAnimationActive
            animationDuration={700}
          >
            {dados.map((fatia, indice) => (
              <Cell
                key={fatia.rotulo}
                fill={corDaSerie(fatia.ehOutros ? Number.MAX_SAFE_INTEGER : indice)}
              />
            ))}
          </Pie>
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload?.length) {
                return null
              }
              const fatia = payload[0]?.payload as { rotulo: string; quantidade: number } | undefined
              if (!fatia) {
                return null
              }
              return (
                <DicaDeGrafico
                  titulo={fatia.rotulo}
                  linhas={[
                    { rotulo: 'Veículos', valor: formatarNumero(fatia.quantidade) },
                    {
                      rotulo: 'Participação',
                      valor: `${String(Math.round((fatia.quantidade / total) * 100))}%`,
                    },
                  ]}
                />
              )
            }}
          />
          {/* O total no centro: é o denominador que dá sentido a cada fatia. */}
          <text
            x="50%"
            y="47%"
            textAnchor="middle"
            className="fill-[var(--texto)] text-2xl font-semibold"
            style={{ fontVariantNumeric: 'tabular-nums' }}
          >
            {formatarNumero(total)}
          </text>
          <text x="50%" y="59%" textAnchor="middle" className="fill-[var(--texto-tenue)] text-[11px]">
            veículos
          </text>
        </PieChart>
      </ResponsiveContainer>
    </MolduraDeGrafico>
  )
}
