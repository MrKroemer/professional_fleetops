import { AlertTriangle, ArrowRight, CheckCircle2, Info, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import type { components } from '@/lib/api/schema'
import { cn } from '@/lib/utils'

type Pendencia = components['schemas']['PendenciaResponse']
type Central = components['schemas']['CentralDePendenciasResponse']
type Severidade = Pendencia['severidade']

/**
 * Aparência de cada severidade.
 *
 * <p>Cada uma traz ícone **e** rótulo, nunca só cor: um leitor com deficiência de
 * visão de cores precisa distinguir crítico de informativo, e a distinção não pode
 * depender do vermelho.
 */
const APARENCIA: Record<
  Severidade,
  { icone: typeof ShieldAlert; classe: string; borda: string; fundo: string }
> = {
  CRITICA: {
    icone: ShieldAlert,
    classe: 'text-critico',
    borda: 'border-l-critico',
    fundo: 'bg-critico-suave/40',
  },
  ATENCAO: {
    icone: AlertTriangle,
    classe: 'text-atencao',
    borda: 'border-l-atencao',
    fundo: 'bg-atencao-suave/40',
  },
  INFORMATIVA: {
    icone: Info,
    classe: 'text-informativo',
    borda: 'border-l-informativo',
    fundo: 'bg-informativo-suave/40',
  },
}

const QUANTIDADE_INICIAL = 6

/**
 * Central de pendências (RN-23).
 *
 * <p>Ordenada por severidade, e não por data: uma CNH vencida hoje pesa mais que um
 * cadastro incompleto de meses atrás. Cada item leva à tela que resolve o problema —
 * uma lista que apenas informa obriga o usuário a procurar onde agir.
 */
export function CentralDePendencias({ central }: { central: Central }) {
  const [expandida, definirExpandida] = useState(false)
  const itens = expandida ? central.itens : central.itens.slice(0, QUANTIDADE_INICIAL)
  const restantes = central.itens.length - itens.length

  return (
    <Card className="overflow-hidden">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-borda px-5 py-4">
        <div>
          <h2 className="text-sm font-semibold text-texto">Central de pendências</h2>
          <p className="mt-0.5 text-xs text-texto-suave">
            Lacunas apuradas dos cadastros, da mais urgente para a menos urgente.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <ContadorDeSeveridade severidade="CRITICA" quantidade={central.criticas} />
          <ContadorDeSeveridade severidade="ATENCAO" quantidade={central.atencao} />
          <ContadorDeSeveridade severidade="INFORMATIVA" quantidade={central.informativas} />
        </div>
      </header>

      {central.itens.length === 0 ? (
        <div className="flex flex-col items-center gap-2 px-5 py-12 text-center">
          <CheckCircle2 className="size-8 text-sucesso" aria-hidden="true" />
          <p className="text-sm font-medium text-texto">Nenhuma pendência em aberto</p>
          <p className="max-w-sm text-xs text-texto-suave">
            Todos os condutores estão com CNH válida, as obras ativas têm fornecedores
            credenciados e as locadoras têm tabela de preços do ano.
          </p>
        </div>
      ) : (
        <>
          <ul className="divide-y divide-borda">
            {itens.map((item, indice) => (
              <ItemDePendencia key={`${item.tipo}-${String(item.referencia ?? indice)}`} item={item} indice={indice} />
            ))}
          </ul>

          {restantes > 0 || expandida ? (
            <div className="border-t border-borda bg-fundo-alternativo/40 px-5 py-2.5 text-center">
              <Button
                variante="sutil"
                tamanho="pequeno"
                onClick={() => {
                  definirExpandida(!expandida)
                }}
              >
                {expandida
                  ? 'Mostrar menos'
                  : `Ver as outras ${String(restantes)} pendência${restantes === 1 ? '' : 's'}`}
              </Button>
            </div>
          ) : null}
        </>
      )}
    </Card>
  )
}

function ContadorDeSeveridade({
  severidade,
  quantidade,
}: {
  severidade: Severidade
  quantidade: number
}) {
  const { icone: Icone, classe } = APARENCIA[severidade]
  const rotulos: Record<Severidade, string> = {
    CRITICA: 'críticas',
    ATENCAO: 'atenção',
    INFORMATIVA: 'informativas',
  }
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border border-borda px-2.5 py-1 text-xs',
        quantidade === 0 && 'opacity-50',
      )}
    >
      <Icone className={cn('size-3.5', classe)} aria-hidden="true" />
      <span className="font-semibold tabular-nums text-texto">{quantidade}</span>
      <span className="text-texto-suave">{rotulos[severidade]}</span>
    </span>
  )
}

function ItemDePendencia({ item, indice }: { item: Pendencia; indice: number }) {
  const { icone: Icone, classe, borda, fundo } = APARENCIA[item.severidade]

  const conteudo = (
    <>
      <span className={cn('mt-0.5 shrink-0', classe)}>
        <Icone className="size-4" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium text-texto">{item.titulo}</span>
          <Badge variante="neutra" className="font-mono text-[0.65rem]">
            {item.regra}
          </Badge>
        </span>
        <span className="mt-0.5 block text-xs leading-relaxed text-texto-suave">{item.detalhe}</span>
      </span>
      {item.recurso ? (
        <ArrowRight
          className="mt-0.5 size-4 shrink-0 text-texto-tenue transition-transform group-hover:translate-x-0.5 group-hover:text-marca-forte"
          aria-hidden="true"
        />
      ) : null}
    </>
  )

  const classesDaLinha = cn(
    'surgir group flex w-full items-start gap-3 border-l-2 px-5 py-3 text-left transition-colors',
    borda,
    'hover:' + fundo,
  )

  return (
    <li style={{ '--indice': indice } as React.CSSProperties}>
      {item.recurso ? (
        <Link to={item.recurso} className={classesDaLinha}>
          {conteudo}
          <span className="sr-only">— abrir {item.tipoDescricao.toLowerCase()}</span>
        </Link>
      ) : (
        <div className={classesDaLinha}>{conteudo}</div>
      )}
    </li>
  )
}
