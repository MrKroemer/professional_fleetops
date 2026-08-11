import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { DicaDeGrafico, ItemDeLegenda, MolduraDeGrafico } from './MolduraDeGrafico'
import { formatarNumero } from '@/lib/formatters'
import { EIXO, GRADE, corDaSerie } from '@/lib/graficos'
import type { components } from '@/lib/api/schema'

type Matriz = components['schemas']['MatrizDaFrotaResponse']

/**
 * Frota cruzada por categoria e locadora, em barras agrupadas.
 *
 * O cruzamento revela o que nenhum dos eixos isolados mostra: se a concentração em uma
 * parceira é uniforme ou se alguma categoria depende de fornecedor único — risco
 * operacional que só aparece quando os dois eixos são lidos juntos.
 *
 * Agrupadas, e não empilhadas, porque a pergunta é "quanto cada locadora tem nesta
 * categoria?". Empilhar responderia "quanto é o total da categoria", que o rótulo já diz.
 */
export function BarrasDaMatriz({ matriz }: { matriz: Matriz }) {
  const dados = matriz.linhas.map((linha) => ({
    rotulo: linha.rotulo,
    total: linha.total,
    ...Object.fromEntries(matriz.locadoras.map((locadora) => [locadora, linha.porLocadora[locadora] ?? 0])),
  }))

  const dependencias = matriz.linhas.filter((linha) => {
    const valores = matriz.locadoras.map((l) => linha.porLocadora[l] ?? 0)
    const maior = Math.max(...valores, 0)
    return linha.total > 0 && maior / linha.total >= 0.9
  })

  return (
    <MolduraDeGrafico
      titulo="Frota por categoria e locadora"
      leitura={
        dependencias.length > 0
          ? `${dependencias.map((d) => d.rotulo).join(' e ')} ${dependencias.length === 1 ? 'depende' : 'dependem'} de uma única locadora em 90% ou mais dos veículos.`
          : 'A frota está distribuída entre as parceiras em todas as categorias.'
      }
      legenda={matriz.locadoras.map((locadora, indice) => (
        <ItemDeLegenda key={locadora} cor={corDaSerie(indice)} rotulo={locadora} />
      ))}
      alturaDoDesenho={260}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={{ top: 4, right: 8, bottom: 4, left: -12 }} barGap={2}>
          <CartesianGrid {...GRADE} />
          <XAxis dataKey="rotulo" {...EIXO} />
          <YAxis {...EIXO} allowDecimals={false} />
          <Tooltip
            cursor={{ fill: 'var(--fundo-alternativo)' }}
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) {
                return null
              }
              return (
                <DicaDeGrafico
                  titulo={String(label)}
                  linhas={payload.map((item) => ({
                    cor: typeof item.color === 'string' ? item.color : undefined,
                    rotulo: String(item.name),
                    valor: formatarNumero(Number(item.value)),
                  }))}
                />
              )
            }}
          />
          {matriz.locadoras.map((locadora, indice) => (
            <Bar
              key={locadora}
              dataKey={locadora}
              name={locadora}
              fill={corDaSerie(indice)}
              /* Topo arredondado na extremidade do dado, base ancorada na linha zero. */
              radius={[4, 4, 0, 0]}
              maxBarSize={38}
              animationDuration={700}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </MolduraDeGrafico>
  )
}
