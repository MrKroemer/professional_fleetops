import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  CheckCircle2,
  FileWarning,
  Fuel,
  Gauge,
  Plus,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { CartaoDeFechamento } from './componentes/CartaoDeFechamento'
import { DialogoDeLancamento, type TipoDeLancamento } from './componentes/DialogoDeLancamento'
import { FaturasDoContrato } from './componentes/FaturasDoContrato'
import { UsoParticularDoContrato } from './componentes/UsoParticularDoContrato'
import { competenciaPadrao, limitesDaCompetencia, STATUS_DE_FATURA } from './tipos'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData, formatarMoeda } from '@/lib/formatters'

/**
 * Operação mensal (Fase 3).
 *
 * A tela é organizada por **competência**, e não por contrato, porque é assim que o
 * trabalho acontece: no início do mês o gestor confere o mês que fechou, contrato a
 * contrato. Escolher o mês primeiro e o contrato depois inverteria a rotina.
 *
 * O bloco de excedentes fica no topo e não depende de escolher contrato: é a resposta da
 * RN-06 à pergunta "quem estourou a franquia?", que nas planilhas só aparecia quando a
 * fatura já tinha chegado com a cobrança.
 */
export function OperacaoPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [competencia, definirCompetencia] = useState(competenciaPadrao())
  const [contratoId, definirContratoId] = useState<number | null>(null)
  const [lancando, definirLancando] = useState<TipoDeLancamento | null>(null)

  const contratos = useQuery({
    queryKey: ['contratos', 'ativos-para-operacao'],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/contratos', {
          params: { query: { status: 'ATIVO', page: 0, size: 200, sort: ['dataRetirada,desc'] } },
        }),
      ),
  })

  const excedentes = useQuery({
    queryKey: ['operacao', 'excedentes', competencia],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/excedentes', { params: { query: { competencia } } }),
      ),
  })

  const divergentes = useQuery({
    queryKey: ['operacao', 'faturas-divergentes'],
    queryFn: async () =>
      exigirSucesso(await api.GET('/api/v1/operacao/faturas/divergentes', {})),
  })

  const fechamento = useQuery({
    queryKey: ['operacao', 'fechamento', contratoId, competencia],
    enabled: contratoId != null,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/fechamento', {
          params: { path: { contratoId: contratoId ?? 0 }, query: { competencia } },
        }),
      ),
  })

  const periodo = limitesDaCompetencia(competencia)

  const registros = useQuery({
    queryKey: ['operacao', 'km', contratoId, competencia],
    enabled: contratoId != null,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/km', {
          params: { path: { contratoId: contratoId ?? 0 }, query: periodo },
        }),
      ),
  })

  const abastecimentos = useQuery({
    queryKey: ['operacao', 'abastecimentos', contratoId, competencia],
    enabled: contratoId != null,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/abastecimentos', {
          params: { path: { contratoId: contratoId ?? 0 }, query: periodo },
        }),
      ),
  })

  const servicos = useQuery({
    queryKey: ['operacao', 'servicos', contratoId, competencia],
    enabled: contratoId != null,
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/operacao/contratos/{contratoId}/servicos', {
          params: { path: { contratoId: contratoId ?? 0 }, query: periodo },
        }),
      ),
  })

  const conferencia = useMutation({
    mutationFn: async (acao: 'conferir' | 'reabrir') =>
      exigirSucesso(
        acao === 'conferir'
          ? await api.POST('/api/v1/operacao/contratos/{contratoId}/fechamento/conferencia', {
              params: { path: { contratoId: contratoId ?? 0 }, query: { competencia } },
              body: {},
            })
          : await api.POST('/api/v1/operacao/contratos/{contratoId}/fechamento/reabertura', {
              params: { path: { contratoId: contratoId ?? 0 }, query: { competencia } },
            }),
      ),
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
    },
  })

  /**
   * Exclusão de um lançamento.
   *
   * Lógica, nunca física — o mesmo princípio de todo o sistema. Um abastecimento lançado
   * por engano sai do fechamento, mas continua na trilha do Envers, que é onde a
   * conferência de auditoria vai procurar.
   */
  const exclusao = useMutation({
    mutationFn: async ({ tipo, id }: { tipo: TipoDeLancamento; id: number }) => {
      if (tipo === 'KM') {
        return exigirSucesso(
          await api.DELETE('/api/v1/operacao/km/{id}', { params: { path: { id } } }),
        )
      }
      if (tipo === 'ABASTECIMENTO') {
        return exigirSucesso(
          await api.DELETE('/api/v1/operacao/abastecimentos/{id}', { params: { path: { id } } }),
        )
      }
      return exigirSucesso(
        await api.DELETE('/api/v1/operacao/servicos/{id}', { params: { path: { id } } }),
      )
    },
    onSuccess: async () => {
      await clienteDeConsultas.invalidateQueries({ queryKey: ['operacao'] })
    },
  })

  const listaDeContratos = contratos.data?.conteudo ?? []

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Operação mensal"
        descricao="Quilometragem, abastecimentos e serviços do mês, com o fechamento calculado a partir dos lançamentos. Escolha a competência e o contrato para conferir."
      />

      <div className="mb-6 flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1.5">
          <span className="text-sm font-medium text-texto">Competência</span>
          <Input
            type="month"
            value={competencia}
            onChange={(evento) => definirCompetencia(evento.target.value)}
            className="w-44"
          />
        </label>
        <label className="flex min-w-64 flex-1 flex-col gap-1.5">
          <span className="text-sm font-medium text-texto">Contrato</span>
          <Select
            value={contratoId ?? ''}
            onChange={(evento) =>
              definirContratoId(evento.target.value ? Number(evento.target.value) : null)
            }
          >
            <option value="">Selecione um contrato para ver o fechamento…</option>
            {listaDeContratos.map((contrato) => (
              <option key={contrato.id} value={contrato.id}>
                {contrato.obraNome} — {contrato.placa ?? 'sem veículo'}
              </option>
            ))}
          </Select>
        </label>
      </div>

      {/* RN-06 — quem estourou a franquia na competência. */}
      <section className="mb-6">
        <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-texto">
          <Gauge className="size-4 text-critico" aria-hidden="true" />
          Acima da franquia em {competencia}
        </h2>
        {excedentes.isPending ? (
          <Skeleton className="h-20 w-full" />
        ) : (excedentes.data ?? []).length === 0 ? (
          <p className="rounded-[var(--radius-base)] border border-borda bg-superficie px-3 py-2.5 text-sm text-texto-suave">
            <CheckCircle2 className="mr-1.5 inline size-4 text-sucesso" aria-hidden="true" />
            Nenhum contrato ultrapassou a franquia de KM nesta competência.
          </p>
        ) : (
          <ul className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
            {(excedentes.data ?? []).map((item, indice) => (
              <li
                key={item.contratoId}
                className="surgir rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/30 px-3 py-2.5"
                style={{ '--indice': indice } as React.CSSProperties}
              >
                <button
                  type="button"
                  onClick={() => definirContratoId(item.contratoId)}
                  className="w-full text-left"
                >
                  <p className="font-mono text-sm font-medium text-texto">{item.placa ?? '—'}</p>
                  <p className="truncate text-xs text-texto-suave">{item.obra}</p>
                  <p className="mt-1 text-sm text-texto">
                    <strong>{item.kmExcedente.toLocaleString('pt-BR')} km</strong> acima ·{' '}
                    {item.vigenciaIndisponivel ? 'sem vigência' : formatarMoeda(item.custoDoExcedente)}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* RN-13 — faturas com divergência ainda sem tratativa concluída. */}
      {(divergentes.data ?? []).length > 0 ? (
        <section className="mb-6">
          <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold text-texto">
            <FileWarning className="size-4 text-atencao" aria-hidden="true" />
            Faturas com divergência em aberto
          </h2>
          <ul className="divide-y divide-borda rounded-[var(--radius-base)] border border-borda bg-superficie">
            {(divergentes.data ?? []).map((fatura) => (
              <li key={fatura.id} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
                <Link
                  to={`/contratos/${String(fatura.contratoId)}`}
                  className="min-w-0 flex-1 text-sm hover:underline"
                >
                  <span className="font-medium text-texto">{fatura.obra}</span>
                  <span className="text-texto-suave"> · {fatura.competencia}</span>
                  {fatura.placa ? (
                    <span className="ml-1 font-mono text-xs text-texto-suave">{fatura.placa}</span>
                  ) : null}
                </Link>
                <span
                  className={`text-sm font-medium ${
                    (fatura.divergencia ?? 0) > 0 ? 'text-critico' : 'text-informativo'
                  }`}
                >
                  {formatarMoeda(fatura.divergencia)}
                </span>
                <Badge variante="atencao">
                  {STATUS_DE_FATURA[fatura.status] ?? fatura.statusDescricao}
                </Badge>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {contratoId == null ? (
        <EstadoVazio
          icone={<Sparkles className="size-6" />}
          titulo="Escolha um contrato"
          descricao="O fechamento é por veículo. Selecione um contrato acima para ver a quilometragem, os abastecimentos, os serviços e o custo apurado do mês."
        />
      ) : fechamento.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : fechamento.data ? (
        <div className="space-y-6">
          <CartaoDeFechamento fechamento={fechamento.data} />

          {podeEditar ? (
            <div className="flex justify-end gap-2">
              {fechamento.data.status === 'CONFERIDO' ? (
                <Button
                  variante="secundaria"
                  onClick={() => conferencia.mutate('reabrir')}
                  disabled={conferencia.isPending}
                >
                  Reabrir competência
                </Button>
              ) : (
                <Button
                  onClick={() => conferencia.mutate('conferir')}
                  disabled={conferencia.isPending}
                >
                  <CheckCircle2 aria-hidden="true" />
                  Marcar como conferida
                </Button>
              )}
            </div>
          ) : null}

          {/* Lançamentos do mês, na ordem em que compõem o fechamento. */}
          <Lancamentos
            titulo="Quilometragem"
            icone={<Gauge className="size-4 text-marca-forte" aria-hidden="true" />}
            vazio="Nenhum registro de KM neste mês."
            aoLancar={podeEditar ? () => definirLancando('KM') : undefined}
            aoExcluir={podeEditar ? (id) => exclusao.mutate({ tipo: 'KM', id }) : undefined}
            itens={(registros.data ?? []).map((registro) => ({
              chave: registro.id,
              principal: `${registro.kmInicial.toLocaleString('pt-BR')} → ${registro.kmFinal.toLocaleString('pt-BR')}`,
              secundario: [registro.origem, registro.destino].filter(Boolean).join(' → '),
              data: registro.data,
              valor: `${registro.kmPercorrido.toLocaleString('pt-BR')} km`,
            }))}
          />

          <Lancamentos
            titulo="Abastecimentos"
            icone={<Fuel className="size-4 text-marca-forte" aria-hidden="true" />}
            vazio="Nenhum abastecimento neste mês."
            aoLancar={podeEditar ? () => definirLancando('ABASTECIMENTO') : undefined}
            aoExcluir={podeEditar ? (id) => exclusao.mutate({ tipo: 'ABASTECIMENTO', id }) : undefined}
            itens={(abastecimentos.data ?? []).map((item) => ({
              chave: item.id,
              principal: item.posto ?? 'posto não informado',
              secundario: item.litros
                ? `${item.litros} L · ${formatarMoeda(item.precoPorLitro)}/L`
                : undefined,
              data: item.data,
              valor: formatarMoeda(item.valor),
              alerta: item.naoConforme ? (item.justificativa ?? 'Não conforme') : undefined,
            }))}
          />

          <Lancamentos
            titulo="Serviços"
            icone={<Sparkles className="size-4 text-marca-forte" aria-hidden="true" />}
            vazio="Nenhum serviço neste mês."
            aoLancar={podeEditar ? () => definirLancando('SERVICO') : undefined}
            aoExcluir={podeEditar ? (id) => exclusao.mutate({ tipo: 'SERVICO', id }) : undefined}
            itens={(servicos.data ?? []).map((item) => ({
              chave: item.id,
              principal: item.tipoDescricao,
              secundario: [item.fornecedor, item.descricao].filter(Boolean).join(' · '),
              data: item.data,
              valor: formatarMoeda(item.valor),
              alerta: item.naoConforme ? (item.justificativa ?? 'Não conforme') : undefined,
            }))}
          />
          <FaturasDoContrato
            contratoId={contratoId}
            competencia={competencia}
            podeEditar={podeEditar}
          />

          <UsoParticularDoContrato contratoId={contratoId} podeEditar={podeEditar} />
        </div>
      ) : null}

      {lancando != null && contratoId != null ? (
        <DialogoDeLancamento
          contratoId={contratoId}
          tipo={lancando}
          aberto
          aoFechar={() => definirLancando(null)}
        />
      ) : null}
    </div>
  )
}

interface ItemDeLancamento {
  chave: number
  principal: string
  secundario?: string | undefined
  data: string
  valor: string
  alerta?: string | undefined
}

/**
 * Lista de lançamentos de um tipo.
 *
 * Um componente para os três porque a forma é a mesma — data, descrição, valor — e a
 * única variação real é a marca de não conformidade, que a RN-04 e a RN-05 tratam
 * igualmente. A justificativa aparece junto do lançamento, e não escondida atrás de um
 * ícone: é ela que explica por que o lançamento existe apesar de irregular.
 */
function Lancamentos({
  titulo,
  icone,
  itens,
  vazio,
  aoLancar,
  aoExcluir,
}: {
  titulo: string
  icone: React.ReactNode
  itens: ItemDeLancamento[]
  vazio: string
  aoLancar?: (() => void) | undefined
  aoExcluir?: ((id: number) => void) | undefined
}) {
  return (
    <section>
      <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-texto">
        {icone}
        {titulo}
        <span className="text-xs font-normal text-texto-tenue">({itens.length})</span>
        {aoLancar ? (
          <Button variante="sutil" tamanho="pequeno" className="ml-auto" onClick={aoLancar}>
            <Plus aria-hidden="true" />
            Lançar
          </Button>
        ) : null}
      </h3>
      {itens.length === 0 ? (
        <p className="rounded-[var(--radius-base)] border border-dashed border-borda px-3 py-2.5 text-sm text-texto-tenue">
          {vazio}
        </p>
      ) : (
        <ul className="divide-y divide-borda rounded-[var(--radius-base)] border border-borda bg-superficie">
          {itens.map((item) => (
            <li key={item.chave} className="flex flex-wrap items-start gap-3 px-3 py-2.5">
              <span className="w-20 shrink-0 text-sm text-texto-suave">
                {formatarData(item.data)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm text-texto">{item.principal}</span>
                {item.secundario ? (
                  <span className="block text-xs text-texto-suave">{item.secundario}</span>
                ) : null}
                {item.alerta ? (
                  <span className="mt-0.5 flex items-start gap-1.5 text-xs text-atencao">
                    <AlertTriangle className="mt-0.5 size-3 shrink-0" aria-hidden="true" />
                    {item.alerta}
                  </span>
                ) : null}
              </span>
              <span className="shrink-0 text-sm font-medium text-texto">{item.valor}</span>
              {aoExcluir ? (
                <button
                  type="button"
                  onClick={() => aoExcluir(item.chave)}
                  className="shrink-0 rounded p-1 text-texto-tenue transition-colors hover:text-critico"
                  aria-label={`Excluir o lançamento de ${item.principal}`}
                >
                  <Trash2 className="size-3.5" aria-hidden="true" />
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
