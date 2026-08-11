import {
  ArrowLeftRight,
  Car,
  CircleCheck,
  CircleDashed,
  PackageCheck,
  UserRound,
  UserRoundCog,
} from 'lucide-react'

import { APARENCIA_DO_MARCO, type Marco } from '../tipos'
import { formatarData } from '@/lib/formatters'
import { cn } from '@/lib/utils'

const ICONES: Record<string, typeof Car> = {
  VEICULO_INICIAL: Car,
  SUBSTITUICAO_VEICULO: ArrowLeftRight,
  CONDUTOR_INICIAL: UserRound,
  TROCA_CONDUTOR: UserRoundCog,
  EVENTO_RETIRADA: PackageCheck,
  EVENTO_DEVOLUCAO: PackageCheck,
}

/**
 * Linha do tempo do contrato — o requisito "linha do tempo visual" da Fase 2.
 *
 * Funde veículos, condutores e eventos em uma coluna só, do mais recente para o mais
 * antigo. Três listas separadas obrigariam a cruzar datas com o dedo na tela; juntas,
 * respondem de uma vez "o que aconteceu com este contrato".
 *
 * Cada natureza de marco tem cor **e** ícone. A cor sozinha excluiria quem não a separa,
 * e é justamente a distinção veículo/condutor que dá sentido à leitura.
 *
 * O trilho vertical é desenhado por uma borda no contêiner de cada item, e não por um
 * elemento absoluto atrás da lista: assim ele acompanha a altura real do conteúdo, que
 * varia com o tamanho do motivo escrito.
 */
export function LinhaDoTempo({ marcos }: { marcos: Marco[] }) {
  if (marcos.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-texto-tenue">
        Este contrato ainda não tem histórico registrado.
      </p>
    )
  }

  return (
    <ol className="relative">
      {marcos.map((marco, indice) => {
        const Icone = ICONES[marco.tipo] ?? CircleDashed
        const aparencia = APARENCIA_DO_MARCO[marco.tipo] ?? {
          cor: 'text-texto-suave',
          anel: 'bg-fundo-alternativo ring-borda',
        }
        const ultimo = indice === marcos.length - 1

        return (
          <li
            key={`${marco.tipo}-${String(marco.referenciaId)}-${marco.data}`}
            className="surgir relative flex gap-4 pb-6 last:pb-0"
            style={{ '--indice': indice } as React.CSSProperties}
          >
            {/* Trilho: para no último item, para não sobrar um traço solto no fim. */}
            {!ultimo ? (
              <span
                aria-hidden="true"
                className="absolute bottom-0 left-[1.125rem] top-9 w-px bg-borda"
              />
            ) : null}

            <span
              className={cn(
                'relative z-10 grid size-9 shrink-0 place-items-center rounded-full ring-4',
                aparencia.anel,
              )}
              aria-hidden="true"
            >
              <Icone className={cn('size-4', aparencia.cor)} />
            </span>

            <div className="min-w-0 flex-1 pt-1">
              <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
                <p
                  className={cn(
                    'font-medium text-texto',
                    marco.tipo.startsWith('VEICULO') || marco.tipo.startsWith('SUBSTITUICAO')
                      ? 'font-mono'
                      : '',
                  )}
                >
                  {marco.rotulo}
                </p>
                <span className={cn('text-xs font-medium', aparencia.cor)}>
                  {marco.tipoDescricao}
                </span>
                {marco.emCurso ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-sucesso-suave px-2 py-0.5 text-[0.68rem] font-medium text-sucesso">
                    <CircleCheck className="size-3" aria-hidden="true" />
                    em curso
                  </span>
                ) : null}
              </div>

              <p className="mt-0.5 text-sm text-texto-suave">
                {/* O período fechado aparece como intervalo; o aberto, com a data de início
                    e o rótulo "em curso" acima — repetir "até hoje" seria ruído. */}
                {formatarData(marco.data)}
                {marco.fim ? ` → ${formatarData(marco.fim)}` : ''}
                {marco.detalhe ? ` · ${marco.detalhe}` : ''}
              </p>

              {marco.motivo ? (
                <p className="mt-1 text-sm italic text-texto-tenue">{marco.motivo}</p>
              ) : null}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
