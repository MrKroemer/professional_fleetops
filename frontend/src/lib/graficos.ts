/**
 * Parâmetros visuais dos gráficos.
 *
 * As cores vêm das variáveis do tema, não de literais: um gráfico com `#E37501` no
 * código ficaria laranja também no tema escuro, onde o passo correto é outro. O SVG
 * aceita `var(--…)` em `fill` e `stroke`, então a troca de tema acontece sozinha.
 *
 * O teto de três séries categóricas é o resultado da validação da paleta — com quatro
 * matizes, duas delas deixam de ser distinguíveis sob deuteranopia no tema escuro.
 * A cauda é dobrada em "Outros", que usa o cinza de desênfase.
 */

export const SERIES = ['var(--serie-1)', 'var(--serie-2)', 'var(--serie-3)'] as const
export const SERIE_NEUTRA = 'var(--serie-neutra)'
export const MAXIMO_DE_SERIES = SERIES.length

/** Cor da série na posição informada; além do teto, o cinza de desênfase. */
export function corDaSerie(indice: number): string {
  return SERIES[indice] ?? SERIE_NEUTRA
}

/** Eixos e grade recessivos: a marca de dado é o que deve saltar, não a moldura. */
export const EIXO = {
  stroke: 'var(--borda-forte)',
  fontSize: 11,
  tickLine: false,
  axisLine: false,
} as const

export const GRADE = {
  stroke: 'var(--borda)',
  strokeDasharray: '3 3',
  vertical: false,
} as const

/**
 * Dobra a cauda de uma distribuição em "Outros".
 *
 * Devolve no máximo `MAXIMO_DE_SERIES` classes mais a agregação do restante, para que
 * nenhum gráfico precise de uma quarta cor categórica.
 */
export function dobrarCauda<T extends { rotulo: string; quantidade: number }>(
  fatias: T[],
  maximo = MAXIMO_DE_SERIES,
): { rotulo: string; quantidade: number; ehOutros: boolean }[] {
  if (fatias.length <= maximo) {
    return fatias.map((fatia) => ({ ...fatia, ehOutros: false }))
  }
  const principais = fatias.slice(0, maximo).map((fatia) => ({ ...fatia, ehOutros: false }))
  const cauda = fatias.slice(maximo)
  return [
    ...principais,
    {
      rotulo: `Outros (${String(cauda.length)})`,
      quantidade: cauda.reduce((soma, fatia) => soma + fatia.quantidade, 0),
      ehOutros: true,
    },
  ]
}
