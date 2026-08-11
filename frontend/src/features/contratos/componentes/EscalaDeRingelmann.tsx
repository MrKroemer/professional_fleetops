import { cn } from '@/lib/utils'

/**
 * Geometria de uma fatia, transcrita de `pages_fleet/experience.html`.
 *
 * O caminho desenha um setor de 72° entre os raios 20 e 50; as cinco fatias são o mesmo
 * caminho girado. Reaproveitá-lo garante que as fatias fechem exatamente, o que cinco
 * caminhos escritos à mão não garantiriam.
 */
const FATIA = 'M -11.75 -16.18 A 20 20 0 0 1 11.75 -16.18 L 29.38 -40.45 A 50 50 0 0 0 -29.38 -40.45 Z'

/**
 * Os cinco padrões, do mais claro ao mais escuro.
 *
 * Os tons são os do instrumento físico e por isso não vêm do tema: a cartela é uma régua
 * de comparação visual, e alterá-la para combinar com o tema escuro destruiria a única
 * coisa que ela precisa fazer — parecer com a fumaça que o avaliador está olhando.
 */
const PADROES = [
  { codigo: 1, percentual: 20, cor: '#cccccc', textoEscuro: true },
  { codigo: 2, percentual: 40, cor: '#999999', textoEscuro: false },
  { codigo: 3, percentual: 60, cor: '#666666', textoEscuro: false },
  { codigo: 4, percentual: 80, cor: '#333333', textoEscuro: false },
  { codigo: 5, percentual: 100, cor: '#000000', textoEscuro: false },
]

interface Props {
  valor: number | null
  aoEscolher: (padrao: number) => void
  /** Limite de aprovação vigente, para marcar as fatias que reprovam. */
  limite: number
  desabilitado?: boolean
}

/**
 * Cartela de Ringelmann interativa — o FOR.MA.01 na tela (RN-09).
 *
 * Reproduz o instrumento de campo: um disco de cinco tons com um orifício central pelo
 * qual se olha a fumaça. É a razão de existir de um controle gráfico aqui em vez de um
 * `select` de 1 a 5 — o avaliador compara, não escolhe de uma lista.
 *
 * A escolha é um grupo de rádio de verdade, não `div`s com `onClick`: as setas percorrem
 * as fatias e o leitor de tela anuncia "Padrão 3, 60%, reprova". A geometria é bonita,
 * mas o registro precisa ser possível sem enxergá-la.
 *
 * As fatias que reprovam ganham um contorno vermelho **e** a palavra "reprova" no rótulo
 * acessível — o limite muda com a altitude, e adivinhar onde ele caiu pela cor não é
 * leitura, é sorte.
 */
export function EscalaDeRingelmann({ valor, aoEscolher, limite, desabilitado = false }: Props) {
  return (
    <div
      role="radiogroup"
      aria-label="Padrão observado na escala de Ringelmann"
      className="flex flex-col items-center gap-3"
    >
      <div className="relative size-[260px]">
        <svg viewBox="-55 -55 110 110" className="absolute inset-0 size-full" aria-hidden="true">
          {PADROES.map((padrao, indice) => {
            const selecionado = valor === padrao.codigo
            const reprova = padrao.codigo > limite
            return (
              <g
                key={padrao.codigo}
                transform={`rotate(${String(indice * 72)})`}
                className={cn(!desabilitado && 'cursor-pointer')}
                onClick={() => {
                  if (!desabilitado) {
                    aoEscolher(padrao.codigo)
                  }
                }}
              >
                <path
                  d={FATIA}
                  fill={padrao.cor}
                  stroke={selecionado ? 'var(--marca)' : reprova ? 'var(--critico)' : 'transparent'}
                  strokeWidth={selecionado ? 2.5 : reprova ? 1 : 0}
                  className="transition-[stroke-width,transform] duration-200"
                  style={{ transformOrigin: '0 0', transform: selecionado ? 'scale(1.04)' : undefined }}
                />
                <text
                  x="0"
                  y="-32"
                  transform={`rotate(${String(-indice * 72)}, 0, -32)`}
                  textAnchor="middle"
                  dominantBaseline="central"
                  fontSize="6"
                  fontWeight="bold"
                  fill={padrao.textoEscuro ? '#000000' : '#ffffff'}
                >
                  {padrao.percentual}%
                </text>
              </g>
            )
          })}
        </svg>

        {/* Orifício central: no instrumento real é vazado, e é por ele que se olha a
            fumaça. Aqui ele mostra a escolha, que é a informação equivalente. */}
        <div className="pointer-events-none absolute left-1/2 top-1/2 grid size-[100px] -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border-2 border-dashed border-borda bg-fundo-alternativo text-center">
          {valor ? (
            <div>
              <p className="text-lg font-bold leading-none text-texto">{valor}</p>
              <p className="text-[0.6rem] uppercase tracking-wide text-texto-suave">
                {PADROES.find((p) => p.codigo === valor)?.percentual}%
              </p>
            </div>
          ) : (
            <span className="px-2 text-[0.6rem] font-semibold uppercase leading-tight text-texto-tenue">
              compare a fumaça
            </span>
          )}
        </div>
      </div>

      {/* Os rádios de verdade. Visualmente discretos, mas focáveis e anunciáveis — é por
          eles que a escolha acontece para quem navega por teclado. */}
      <div className="flex flex-wrap justify-center gap-1.5">
        {PADROES.map((padrao) => {
          const reprova = padrao.codigo > limite
          return (
            <label
              key={padrao.codigo}
              className={cn(
                'flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-base)] border px-2.5 py-1 text-xs transition-colors',
                valor === padrao.codigo
                  ? 'border-marca bg-marca-suave font-semibold text-marca-forte'
                  : 'border-borda text-texto-suave hover:bg-fundo-alternativo',
                desabilitado && 'cursor-not-allowed opacity-60',
              )}
            >
              <input
                type="radio"
                name="padrao-ringelmann"
                value={padrao.codigo}
                checked={valor === padrao.codigo}
                disabled={desabilitado}
                onChange={() => {
                  aoEscolher(padrao.codigo)
                }}
                className="size-3 accent-[var(--marca)]"
              />
              <span aria-hidden="true">
                {padrao.codigo} · {padrao.percentual}%
              </span>
              <span className="sr-only">
                Padrão {padrao.codigo}, {padrao.percentual} por cento,{' '}
                {reprova ? 'reprova' : 'aprova'}
              </span>
              {reprova ? (
                <span
                  className="size-1.5 rounded-full bg-critico"
                  aria-hidden="true"
                  title="Reprova no limite vigente"
                />
              ) : null}
            </label>
          )
        })}
      </div>
    </div>
  )
}
