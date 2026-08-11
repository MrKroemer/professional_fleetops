import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

import { DicaDeGrafico, ItemDeLegenda, MolduraDeGrafico } from './MolduraDeGrafico'
import { formatarMoeda, formatarNumero } from '@/lib/formatters'
import { EIXO, GRADE, corDaSerie } from '@/lib/graficos'
import type { components } from '@/lib/api/schema'

type Curva = components['schemas']['CurvaDeLocadoraResponse']

/**
 * Curva de preço médio por pacote de quilometragem, uma linha por locadora.
 *
 * É a comparação que a planilha não permite: as grades da Unidas e da Localiza ficam
 * lado a lado, com conjuntos de pacotes diferentes, e ninguém consegue ver em que faixa
 * cada uma passa a ser mais cara. Aqui as duas curvas dividem o mesmo eixo de
 * quilometragem e a diferença fica visível.
 *
 * Eixo único, sempre: duas escalas de valor no mesmo desenho fariam qualquer par de
 * curvas parecer o que o autor quisesse.
 */
export function LinhasDePreco({ curvas, ano }: { curvas: Curva[]; ano: number }) {
  // As locadoras têm pacotes distintos; o eixo é a união ordenada de todos eles, e cada
  // curva simplesmente não tem ponto onde não oferece aquele pacote.
  const pacotes = [
    ...new Set(curvas.flatMap((curva) => curva.pontos.map((ponto) => ponto.pacoteKm))),
  ].sort((um, outro) => um - outro)

  const dados = pacotes.map((pacote) => {
    const linha: Record<string, number | null> = { pacoteKm: pacote }
    for (const curva of curvas) {
      linha[curva.locadora] = curva.pontos.find((p) => p.pacoteKm === pacote)?.valorMedio ?? null
    }
    return linha
  })

  const leitura = montarLeitura(curvas, pacotes)

  return (
    <MolduraDeGrafico
      titulo={`Curva de preço por pacote de KM · ${String(ano)}`}
      leitura={leitura}
      legenda={curvas.map((curva, indice) => (
        <ItemDeLegenda key={curva.locadora} cor={corDaSerie(indice)} rotulo={curva.locadora} />
      ))}
      alturaDoDesenho={260}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={dados} margin={{ top: 8, right: 16, bottom: 4, left: 4 }}>
          <CartesianGrid {...GRADE} />
          <XAxis
            dataKey="pacoteKm"
            {...EIXO}
            tickFormatter={(valor: number) => `${formatarNumero(valor)} km`}
          />
          <YAxis
            {...EIXO}
            width={64}
            tickFormatter={(valor: number) => `R$ ${String(Math.round(valor / 1000))} mil`}
          />
          <Tooltip
            cursor={{ stroke: 'var(--borda-forte)', strokeWidth: 1 }}
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) {
                return null
              }
              return (
                <DicaDeGrafico
                  titulo={`Pacote de ${formatarNumero(Number(label))} km/mês`}
                  linhas={payload
                    .filter((item) => item.value != null)
                    .map((item) => ({
                      cor: typeof item.color === 'string' ? item.color : undefined,
                      rotulo: String(item.name),
                      valor: formatarMoeda(Number(item.value)),
                    }))}
                />
              )
            }}
          />
          {curvas.map((curva, indice) => (
            <Line
              key={curva.locadora}
              type="monotone"
              dataKey={curva.locadora}
              name={curva.locadora}
              stroke={corDaSerie(indice)}
              strokeWidth={2}
              /* Marcador com anel de superfície: sobreposições continuam legíveis. */
              dot={{ r: 4, strokeWidth: 2, stroke: 'var(--superficie)' }}
              activeDot={{ r: 6, strokeWidth: 2, stroke: 'var(--superficie)' }}
              connectNulls
              animationDuration={800}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </MolduraDeGrafico>
  )
}

/** Descreve, em uma frase, o que as curvas revelam no menor pacote comum. */
function montarLeitura(curvas: Curva[], pacotes: number[]): string {
  if (curvas.length === 0 || pacotes.length === 0) {
    return 'Nenhuma tabela de preços com pacotes suficientes para traçar a curva.'
  }
  if (curvas.length === 1) {
    return `Preço médio da ${curvas[0]?.locadora ?? ''} conforme a franquia contratada aumenta.`
  }

  const menorPacote = pacotes[0] ?? 0
  const valores = curvas
    .map((curva) => ({
      locadora: curva.locadora,
      valor: curva.pontos.find((ponto) => ponto.pacoteKm === menorPacote)?.valorMedio,
    }))
    .filter((item): item is { locadora: string; valor: number } => item.valor != null)
    .sort((um, outro) => um.valor - outro.valor)

  const maisBarata = valores[0]
  const maisCara = valores.at(-1)
  if (!maisBarata || !maisCara || maisBarata.locadora === maisCara.locadora) {
    return 'Comparação do preço médio entre as parceiras conforme a franquia aumenta.'
  }

  const diferenca = Math.round(((maisCara.valor - maisBarata.valor) / maisBarata.valor) * 100)
  return `No pacote de ${formatarNumero(menorPacote)} km, a ${maisBarata.locadora} sai ${String(diferenca)}% mais barata que a ${maisCara.locadora} na média dos grupos.`
}
