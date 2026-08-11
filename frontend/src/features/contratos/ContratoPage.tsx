import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  ArrowLeft,
  ArrowLeftRight,
  CalendarSearch,
  Gauge,
  PackageCheck,
  PackageOpen,
  UserRoundCog,
} from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { DialogoDeEncerramento } from './componentes/DialogoDeEncerramento'
import { DialogoDeNovoEvento } from './componentes/DialogoDeNovoEvento'
import { DialogoDeTroca } from './componentes/DialogoDeTroca'
import { LinhaDoTempo } from './componentes/LinhaDoTempo'
import { PainelDoEvento } from './componentes/PainelDoEvento'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData } from '@/lib/formatters'

const CORES_DO_STATUS: Record<string, 'sucesso' | 'atencao' | 'neutra'> = {
  ATIVO: 'sucesso',
  DESMOBILIZADO: 'atencao',
  DEVOLVIDO: 'neutra',
  INATIVO: 'neutra',
}

/**
 * Página de um contrato — o ciclo de vida inteiro em uma tela (Fase 2).
 *
 * A ordem da página segue a ordem das perguntas: o que é este contrato, o que aconteceu
 * com ele, o que está em aberto agora. As ações ficam no topo porque quem chega aqui
 * geralmente chega para fazer algo — registrar uma troca, concluir uma retirada.
 *
 * Eventos em preenchimento aparecem antes da linha do tempo. É trabalho inacabado: se
 * ficasse depois do histórico, alguém fecharia a página sem ver que faltavam três fotos.
 */
export function ContratoPage() {
  const { id } = useParams<{ id: string }>()
  const contratoId = Number(id)
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [trocando, definirTrocando] = useState<'VEICULO' | 'CONDUTOR' | null>(null)
  const [novoEvento, definirNovoEvento] = useState<'RETIRADA' | 'DEVOLUCAO' | null>(null)
  const [encerrando, definirEncerrando] = useState(false)
  const [dataConsultada, definirDataConsultada] = useState('')

  const contrato = useQuery({
    queryKey: ['contrato', contratoId, 'linha-do-tempo'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/contratos/{id}/linha-do-tempo', {
          params: { path: { id: contratoId } },
        }),
      ),
  })

  const eventos = useQuery({
    queryKey: ['contrato', contratoId, 'eventos'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/contratos/{id}/eventos', { params: { path: { id: contratoId } } }),
      ),
  })

  /**
   * Consulta temporal da RN-18.
   *
   * Disparada por botão, e não a cada tecla digitada na data: uma data parcial como
   * "2025-0" é uma consulta válida sintaticamente e sem sentido nenhum.
   */
  const situacao = useMutation({
    mutationFn: async (data: string) =>
      exigirSucesso(
        await api.GET('/api/v1/contratos/{id}/situacao-em', {
          params: { path: { id: contratoId }, query: { data } },
        }),
      ),
  })

  if (contrato.isPending) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-6 py-8">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (contrato.isError || !contrato.data) {
    return (
      <div className="mx-auto max-w-5xl px-6 py-8">
        <p className="text-sm text-critico">Não foi possível carregar este contrato.</p>
        <Button variante="secundaria" className="mt-3" onClick={() => void contrato.refetch()}>
          Tentar novamente
        </Button>
      </div>
    )
  }

  const dados = contrato.data
  const ativo = dados.status === 'ATIVO'
  const emPreenchimento = (eventos.data ?? []).filter((e) => e.situacao === 'EM_PREENCHIMENTO')
  const concluidos = (eventos.data ?? []).filter((e) => e.situacao === 'CONCLUIDO')

  return (
    <div className="mx-auto max-w-5xl px-6 py-8">
      <Link
        to="/contratos"
        className="inline-flex items-center gap-1.5 text-sm text-texto-suave hover:text-texto"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Contratos
      </Link>

      <header className="mt-3 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-2xl font-semibold tracking-tight text-texto">{dados.obra}</h1>
            <Badge variante={CORES_DO_STATUS[dados.status] ?? 'neutra'}>
              {dados.statusDescricao}
            </Badge>
          </div>
          <p className="mt-1 text-sm text-texto-suave">
            {dados.codigoInterno ? `${dados.codigoInterno} · ` : ''}
            {dados.locadora} · retirada em {formatarData(dados.dataRetirada)}
            {dados.dataEncerramento ? ` · encerrado em ${formatarData(dados.dataEncerramento)}` : ''}
          </p>
          {/* A operação mensal deste contrato fica a um clique: sem isto, quem abre o
              contrato para conferir uma troca precisa voltar ao menu e reencontrá-lo na
              lista da outra tela. */}
          <Link
            to="/operacao"
            className="mt-1.5 inline-flex items-center gap-1.5 text-sm text-marca-forte hover:underline"
          >
            <Gauge className="size-4" aria-hidden="true" />
            Ver a operação mensal deste contrato
          </Link>
        </div>

        {podeEditar && ativo ? (
          <div className="flex flex-wrap gap-2">
            <Button variante="secundaria" tamanho="pequeno" onClick={() => definirTrocando('VEICULO')}>
              <ArrowLeftRight aria-hidden="true" />
              Substituir veículo
            </Button>
            <Button variante="secundaria" tamanho="pequeno" onClick={() => definirTrocando('CONDUTOR')}>
              <UserRoundCog aria-hidden="true" />
              Trocar condutor
            </Button>
            <Button variante="secundaria" tamanho="pequeno" onClick={() => definirNovoEvento('RETIRADA')}>
              <PackageOpen aria-hidden="true" />
              Registrar retirada
            </Button>
            <Button variante="secundaria" tamanho="pequeno" onClick={() => definirNovoEvento('DEVOLUCAO')}>
              <PackageCheck aria-hidden="true" />
              Registrar devolução
            </Button>
            <Button tamanho="pequeno" onClick={() => definirEncerrando(true)}>
              Encerrar contrato
            </Button>
          </div>
        ) : null}
      </header>

      {/* Estado atual, em números — o que a planilha mostrava espalhado em colunas. */}
      <dl className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          { rotulo: 'Veículo atual', valor: dados.veiculoAtual ?? '—', mono: true },
          { rotulo: 'Condutor atual', valor: dados.condutorAtual ?? '—', mono: false },
          {
            rotulo: 'Substituições',
            valor: String(dados.quantidadeDeSubstituicoes),
            mono: false,
          },
          {
            rotulo: 'Trocas de condutor',
            valor: String(dados.quantidadeDeTrocasDeCondutor),
            mono: false,
          },
        ].map((item, indice) => (
          <div
            key={item.rotulo}
            className="surgir rounded-[var(--radius-base)] border border-borda bg-superficie px-3 py-2.5"
            style={{ '--indice': indice } as React.CSSProperties}
          >
            <dt className="text-[0.68rem] font-semibold uppercase tracking-wide text-texto-tenue">
              {item.rotulo}
            </dt>
            <dd
              className={`mt-0.5 truncate font-medium text-texto ${item.mono ? 'font-mono' : ''}`}
            >
              {item.valor}
            </dd>
          </div>
        ))}
      </dl>

      {/* Trabalho inacabado primeiro. */}
      {emPreenchimento.length > 0 ? (
        <section className="mt-6 space-y-4">
          <div className="flex items-center gap-2">
            <AlertTriangle className="size-4 text-atencao" aria-hidden="true" />
            <h2 className="text-sm font-semibold text-texto">
              {emPreenchimento.length === 1
                ? 'Um evento em preenchimento'
                : `${String(emPreenchimento.length)} eventos em preenchimento`}
            </h2>
          </div>
          {emPreenchimento.map((evento) => (
            <PainelDoEvento key={evento.id} evento={evento} podeEditar={podeEditar} />
          ))}
        </section>
      ) : null}

      {/* Consulta temporal — a pergunta literal da RN-18. */}
      <section className="mt-6 rounded-[var(--radius-base)] border border-borda bg-superficie p-4">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-texto">
          <CalendarSearch className="size-4 text-informativo" aria-hidden="true" />
          Quem dirigia o quê em uma data
        </h2>
        <p className="mt-0.5 text-sm text-texto-suave">
          Responde pelo histórico, não pelo estado atual — é o que a RN-18 exige.
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-2">
          <Input
            type="date"
            value={dataConsultada}
            onChange={(evento) => definirDataConsultada(evento.target.value)}
            className="w-44"
            aria-label="Data da consulta"
          />
          <Button
            variante="secundaria"
            disabled={!dataConsultada || situacao.isPending}
            onClick={() => situacao.mutate(dataConsultada)}
          >
            Consultar
          </Button>
          {situacao.data ? (
            <p className="text-sm text-texto">
              {situacao.data.vigente ? (
                <>
                  Em {formatarData(situacao.data.data)}:{' '}
                  <span className="font-mono font-medium">{situacao.data.placa ?? '—'}</span>
                  {situacao.data.modelo ? ` (${situacao.data.modelo})` : ''}
                  {situacao.data.condutor ? ` com ${situacao.data.condutor}` : ', sem condutor registrado'}
                </>
              ) : (
                <span className="text-texto-suave">
                  O contrato não estava vigente em {formatarData(situacao.data.data)}.
                </span>
              )}
            </p>
          ) : null}
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-texto-tenue">
          Linha do tempo
        </h2>
        <LinhaDoTempo marcos={dados.marcos ?? []} />
      </section>

      {concluidos.length > 0 ? (
        <section className="mt-8 space-y-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-texto-tenue">
            Eventos concluídos
          </h2>
          {concluidos.map((evento) => (
            <PainelDoEvento key={evento.id} evento={evento} podeEditar={podeEditar} />
          ))}
        </section>
      ) : null}

      {trocando ? (
        <DialogoDeTroca
          contratoId={contratoId}
          tipo={trocando}
          aberto
          aoFechar={() => definirTrocando(null)}
        />
      ) : null}

      {novoEvento ? (
        <DialogoDeNovoEvento
          contratoId={contratoId}
          tipo={novoEvento}
          aberto
          aoFechar={async () => {
            definirNovoEvento(null)
            await clienteDeConsultas.invalidateQueries({ queryKey: ['contrato', contratoId] })
          }}
        />
      ) : null}

      <DialogoDeEncerramento
        contratoId={contratoId}
        aberto={encerrando}
        aoFechar={() => definirEncerrando(false)}
      />
    </div>
  )
}
