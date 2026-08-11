import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Bell, CheckCircle2, Info, ShieldAlert } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuConteudo,
  DropdownMenuGatilho,
  DropdownMenuRotulo,
  DropdownMenuSeparador,
} from '@/components/ui/dropdown-menu'
import { api, exigirSucesso } from '@/lib/api/client'
import type { components } from '@/lib/api/schema'
import { cn } from '@/lib/utils'

type Pendencia = components['schemas']['PendenciaResponse']
type Severidade = Pendencia['severidade']

/** Quantas pendências cabem no sino antes de mandar o usuário para a central. */
const NO_SINO = 5

/** Reintervalo de reconsulta: a base muda por ação humana, não a cada segundo. */
const INTERVALO_DE_ATUALIZACAO = 60_000

const APARENCIA: Record<Severidade, { icone: typeof ShieldAlert; classe: string }> = {
  CRITICA: { icone: ShieldAlert, classe: 'text-critico' },
  ATENCAO: { icone: AlertTriangle, classe: 'text-atencao' },
  INFORMATIVA: { icone: Info, classe: 'text-informativo' },
}

/**
 * Sino de alertas da barra superior (RN-23).
 *
 * Mostra as pendências mais graves apuradas dos cadastros, não um contador genérico.
 * O ponto sobre o sino só acende quando há algo **crítico** — se piscasse por qualquer
 * pendência informativa, em uma semana ninguém mais olharia para ele.
 *
 * A lista é um atalho, não a central: cinco itens e um caminho para o painel, onde a
 * lista completa vive com o contexto que ela precisa.
 */
export function SinoDeAlertas() {
  const consulta = useQuery({
    queryKey: ['painel', 'pendencias'],
    queryFn: async () => exigirSucesso(await api.GET('/api/v1/painel/pendencias', {})),
    refetchInterval: INTERVALO_DE_ATUALIZACAO,
  })

  const central = consulta.data
  const criticas = central?.criticas ?? 0
  const total = central ? central.criticas + central.atencao + central.informativas : 0
  const itens = central?.itens.slice(0, NO_SINO) ?? []

  return (
    <DropdownMenu>
      <DropdownMenuGatilho asChild>
        <Button
          variante="sutil"
          tamanho="icone"
          className="relative"
          aria-label={
            total === 0
              ? 'Central de pendências: nada em aberto'
              : `Central de pendências: ${String(total)} em aberto, ${String(criticas)} críticas`
          }
        >
          <Bell aria-hidden="true" />
          {criticas > 0 ? (
            <span
              aria-hidden="true"
              className="absolute right-1.5 top-1.5 grid size-4 place-items-center rounded-full bg-critico text-[0.6rem] font-bold leading-none text-white"
            >
              {criticas > 9 ? '9+' : criticas}
            </span>
          ) : null}
        </Button>
      </DropdownMenuGatilho>

      <DropdownMenuConteudo align="end" className="w-96 p-0">
        <div className="flex items-center justify-between px-3 py-2">
          <DropdownMenuRotulo className="px-0">Pendências</DropdownMenuRotulo>
          {total > 0 ? (
            <span className="text-xs text-texto-tenue">
              {criticas} crítica{criticas === 1 ? '' : 's'} de {total}
            </span>
          ) : null}
        </div>
        <DropdownMenuSeparador />

        {consulta.isPending ? (
          <p className="px-3 py-6 text-center text-sm text-texto-tenue">Apurando…</p>
        ) : itens.length === 0 ? (
          <div className="flex flex-col items-center gap-1.5 px-3 py-6 text-center">
            <CheckCircle2 className="size-6 text-sucesso" aria-hidden="true" />
            <p className="text-sm text-texto">Nenhuma pendência em aberto</p>
          </div>
        ) : (
          <ul className="max-h-80 overflow-y-auto">
            {itens.map((item, indice) => {
              const { icone: Icone, classe } = APARENCIA[item.severidade]
              return (
                <li key={`${item.tipo}-${String(item.referencia ?? indice)}`}>
                  <Link
                    to={item.recurso ?? '/'}
                    className="flex items-start gap-2.5 border-b border-borda px-3 py-2.5 transition-colors last:border-0 hover:bg-fundo-alternativo"
                  >
                    <Icone className={cn('mt-0.5 size-4 shrink-0', classe)} aria-hidden="true" />
                    <span className="min-w-0">
                      <span className="block text-sm font-medium leading-snug text-texto">
                        {item.titulo}
                      </span>
                      <span className="mt-0.5 block text-xs leading-snug text-texto-suave">
                        {item.regra} · {item.tipoDescricao}
                      </span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}

        {total > itens.length ? (
          <div className="border-t border-borda p-1.5">
            <Button variante="sutil" tamanho="pequeno" className="w-full" asChild>
              <Link to="/">Ver todas as {total} pendências no painel</Link>
            </Button>
          </div>
        ) : null}
      </DropdownMenuConteudo>
    </DropdownMenu>
  )
}
