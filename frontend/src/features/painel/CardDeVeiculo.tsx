import { ArrowRight, Building2, Fuel, IdCard, Radio, ShieldAlert, Sticker, TriangleAlert } from 'lucide-react'
import type { MouseEvent } from 'react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import type { components } from '@/lib/api/schema'
import { formatarNumero } from '@/lib/formatters'
import { cn } from '@/lib/utils'

type VeiculoEmOperacao = components['schemas']['VeiculoEmOperacaoResponse']

/**
 * Card de um veículo em operação — um por veículo, sem agrupamento.
 *
 * Responde às três perguntas que o gestor faz ao olhar a frota: **qual carro**, **quem
 * está com ele** e **em qual obra**. A placa domina a hierarquia porque é como o veículo
 * é identificado em toda a operação; o condutor vem logo abaixo, com a situação da CNH
 * ao lado — uma habilitação vencida impede o vínculo ao contrato (RN-16) e precisa ser
 * vista sem abrir nada.
 *
 * A seta e o brilho que acompanha o cursor vêm de `pages_fleet/street.html`, reduzidos
 * ao que sobrevive à repetição: numa grade de dezenas de cards, uma revelação deslizante
 * completa por card viraria ruído.
 */
export function CardDeVeiculo({ veiculo, indice = 0 }: { veiculo: VeiculoEmOperacao; indice?: number }) {
  const acompanharCursor = (evento: MouseEvent<HTMLAnchorElement>) => {
    const area = evento.currentTarget.getBoundingClientRect()
    evento.currentTarget.style.setProperty(
      '--cursor-x',
      `${String(((evento.clientX - area.left) / area.width) * 100)}%`,
    )
    evento.currentTarget.style.setProperty(
      '--cursor-y',
      `${String(((evento.clientY - area.top) / area.height) * 100)}%`,
    )
  }

  return (
    <Link
      to={`/cadastros/veiculos/${String(veiculo.veiculoId)}`}
      onMouseMove={acompanharCursor}
      style={{ '--indice': Math.min(indice, 16) } as React.CSSProperties}
      className={cn(
        'brilho-de-marca surgir group relative flex flex-col overflow-hidden rounded-[calc(var(--radius-base)*1.5)]',
        'border border-borda bg-superficie p-4 transition-all duration-300',
        'hover:-translate-y-1 hover:border-marca/40 hover:shadow-lg',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-anel',
        // CNH vencida tinge a borda: é bloqueio operacional, não detalhe de cadastro.
        veiculo.cnhVencida && 'border-critico/40',
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'absolute inset-x-0 top-0 h-0.5 origin-left scale-x-0 transition-transform duration-300 group-hover:scale-x-100',
          veiculo.cnhVencida ? 'bg-critico' : 'bg-marca',
        )}
      />

      <div className="relative z-10 flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-mono text-lg font-semibold leading-none tracking-tight text-texto">
            {veiculo.placaFormatada}
          </p>
          <p className="mt-1 truncate text-sm text-texto">{veiculo.modelo}</p>
        </div>
        <ArrowRight
          className="mt-1 size-4 shrink-0 -translate-x-1 text-texto-tenue opacity-0 transition-all duration-300 group-hover:translate-x-0 group-hover:text-marca-forte group-hover:opacity-100"
          aria-hidden="true"
        />
      </div>

      {/* Condutor e situação da CNH: a informação que decide se o carro pode rodar. */}
      <div className="relative z-10 mt-3 space-y-1.5 border-t border-borda pt-3">
        <p className="flex items-center gap-1.5 text-sm">
          <IdCard className="size-3.5 shrink-0 text-texto-tenue" aria-hidden="true" />
          <span className="min-w-0 truncate text-texto">{veiculo.condutorNome ?? 'Sem condutor'}</span>
        </p>
        <SituacaoDaCnh veiculo={veiculo} />
        <p className="flex items-center gap-1.5 text-xs text-texto-suave">
          <Building2 className="size-3.5 shrink-0 text-texto-tenue" aria-hidden="true" />
          <span className="min-w-0 truncate">
            <span className="font-mono">{veiculo.obraCodigo}</span> · {veiculo.obraNome}
          </span>
        </p>
      </div>

      <div className="relative z-10 mt-3 flex flex-wrap items-center gap-1">
        <Badge variante="neutra">{veiculo.categoriaDescricao}</Badge>
        {veiculo.exigeTesteFumacaPreta ? (
          <Badge variante="atencao">
            <Fuel className="size-3" aria-hidden="true" />
            Diesel
          </Badge>
        ) : null}
        {veiculo.possuiRastreador ? (
          <Badge variante="informativa">
            <Radio className="size-3" aria-hidden="true" />
            <span className="sr-only">Possui </span>Rastreador
          </Badge>
        ) : null}
        {veiculo.possuiAdesivo ? (
          <Badge variante="neutra">
            <Sticker className="size-3" aria-hidden="true" />
            <span className="sr-only">Possui </span>Adesivo
          </Badge>
        ) : null}
      </div>

      <p className="relative z-10 mt-3 border-t border-borda pt-2 text-[0.68rem] text-texto-tenue">
        {veiculo.locadora}
        {veiculo.grupoTarifario ? ` · grupo ${veiculo.grupoTarifario}` : ''}
        {veiculo.pacoteKmContratado
          ? ` · ${formatarNumero(veiculo.pacoteKmContratado)} km/mês`
          : ' · sem franquia'}
      </p>
    </Link>
  )
}

/** Situação da CNH com ícone e texto — a identidade nunca depende só da cor (RN-16). */
function SituacaoDaCnh({ veiculo }: { veiculo: VeiculoEmOperacao }) {
  if (!veiculo.cnhValidade) {
    return (
      <p className="flex items-center gap-1.5 text-xs text-texto-tenue">
        <ShieldAlert className="size-3.5 shrink-0" aria-hidden="true" />
        CNH não cadastrada
      </p>
    )
  }
  if (veiculo.cnhVencida) {
    return (
      <p className="flex items-center gap-1.5 text-xs font-medium text-critico">
        <ShieldAlert className="size-3.5 shrink-0" aria-hidden="true" />
        CNH vencida — vínculo bloqueado
      </p>
    )
  }
  if (veiculo.cnhEmAlerta) {
    return (
      <p className="flex items-center gap-1.5 text-xs font-medium text-atencao">
        <TriangleAlert className="size-3.5 shrink-0" aria-hidden="true" />
        CNH vence em {String(veiculo.diasParaVencerCnh ?? 0)} dias
      </p>
    )
  }
  return (
    <p className="flex items-center gap-1.5 text-xs text-sucesso">
      <IdCard className="size-3.5 shrink-0" aria-hidden="true" />
      CNH em dia
    </p>
  )
}
