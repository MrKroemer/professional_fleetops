import { useQuery } from '@tanstack/react-query'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import { FileSpreadsheet, RefreshCw, UserRoundCog } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { STATUS_DE_CONTRATO, type ContratoResumo, type StatusContrato } from './tipos'
import { Badge } from '@/components/ui/badge'
import { EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { TabelaDeDados } from '@/components/ui/tabela-de-dados'
import { BarraDeFiltros } from '@/features/cadastros/componentes/BarraDeFiltros'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData } from '@/lib/formatters'
import { useDebounce } from '@/lib/use-debounce'

/** Tamanho de página usado só na exportação, para levar a visão filtrada inteira. */
const TUDO = 2000

const CORES_DO_STATUS: Record<string, 'sucesso' | 'atencao' | 'neutra'> = {
  ATIVO: 'sucesso',
  DESMOBILIZADO: 'atencao',
  DEVOLVIDO: 'neutra',
  INATIVO: 'neutra',
}

/**
 * Lista de contratos de locação — a "linha do controle geral" que o sistema substitui.
 *
 * A linha mostra a situação de hoje e, ao lado, quantas trocas já houve. Esse par é o
 * que a planilha nunca conseguiu mostrar junto: lá, o histórico morava em colunas
 * repetidas à direita, fora da vista. Aqui um contrato com três substituições se anuncia
 * antes de ser aberto.
 *
 * Clicar na linha abre a linha do tempo, e não um painel lateral: o ciclo de vida tem
 * ações próprias — retirada, troca, devolução — que precisam de espaço.
 */
export function ContratosPage() {
  const navegar = useNavigate()

  const [termo, definirTermo] = useState('')
  const [status, definirStatus] = useState<StatusContrato | ''>('ATIVO')
  const termoAplicado = useDebounce(termo)

  const [pagina, definirPagina] = useState(0)
  const [tamanho, definirTamanho] = useState(20)
  const [ordenacao, definirOrdenacao] = useState<SortingState>([{ id: 'dataRetirada', desc: true }])

  const filtros = {
    ...(termoAplicado ? { termo: termoAplicado } : {}),
    ...(status ? { status } : {}),
  }
  const ordem = ordenacao[0]
  const sort = [`${ordem?.id ?? 'dataRetirada'},${ordem?.desc ? 'desc' : 'asc'}`]

  const consulta = useQuery({
    queryKey: ['contratos', { filtros, pagina, tamanho, sort }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/contratos', {
          params: { query: { ...filtros, page: pagina, size: tamanho, sort } },
        }),
      ),
  })

  const contratos = consulta.data?.conteudo ?? []
  const filtrando = termoAplicado.length > 0 || status !== ''

  const colunas: ColumnDef<ContratoResumo, unknown>[] = [
    {
      id: 'codigoInterno',
      header: 'Contrato',
      enableHiding: false,
      meta: { rotulo: 'Contrato', exportar: (c) => `${c.codigoInterno ?? ''} ${c.obraNome}` },
      cell: ({ row }) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-texto">{row.original.obraNome}</p>
          <p className="font-mono text-xs text-texto-suave">
            {row.original.obraCodigo}
            {row.original.codigoInterno && row.original.codigoInterno !== row.original.obraCodigo
              ? ` · ${row.original.codigoInterno}`
              : ''}
          </p>
        </div>
      ),
    },
    {
      id: 'veiculo',
      header: 'Veículo atual',
      enableSorting: false,
      meta: { rotulo: 'Veículo atual', exportar: (c) => `${c.placa ?? ''} ${c.modelo ?? ''}`.trim() },
      cell: ({ row }) =>
        row.original.placa ? (
          <div className="min-w-0">
            <p className="font-mono font-medium text-texto">{row.original.placa}</p>
            <p className="truncate text-xs text-texto-suave">{row.original.modelo}</p>
          </div>
        ) : (
          <span className="text-texto-tenue">sem veículo</span>
        ),
    },
    {
      id: 'condutor',
      header: 'Condutor atual',
      enableSorting: false,
      meta: { rotulo: 'Condutor atual', exportar: (c) => c.condutor ?? '' },
      cell: ({ row }) => (
        <span className="text-texto-suave">{row.original.condutor ?? '—'}</span>
      ),
    },
    {
      id: 'locadora',
      header: 'Locadora',
      enableSorting: false,
      meta: { rotulo: 'Locadora', exportar: (c) => c.locadora },
      cell: ({ row }) => <span className="text-texto-suave">{row.original.locadora}</span>,
    },
    {
      id: 'historico',
      header: 'Histórico',
      enableSorting: false,
      meta: {
        rotulo: 'Histórico',
        exportar: (c) =>
          `${String(c.quantidadeDeSubstituicoes)} substituições, ${String(c.quantidadeDeTrocasDeCondutor)} trocas de condutor`,
      },
      cell: ({ row }) => {
        const { quantidadeDeSubstituicoes: veiculos, quantidadeDeTrocasDeCondutor: condutores } =
          row.original
        if (!veiculos && !condutores) {
          return <span className="text-texto-tenue">sem trocas</span>
        }
        return (
          <div className="flex items-center gap-3 text-xs text-texto-suave">
            {veiculos ? (
              <span className="inline-flex items-center gap-1" title="Substituições de veículo">
                <RefreshCw className="size-3.5 text-marca-forte" aria-hidden="true" />
                {veiculos}
                <span className="sr-only">substituições de veículo</span>
              </span>
            ) : null}
            {condutores ? (
              <span className="inline-flex items-center gap-1" title="Trocas de condutor">
                <UserRoundCog className="size-3.5 text-informativo" aria-hidden="true" />
                {condutores}
                <span className="sr-only">trocas de condutor</span>
              </span>
            ) : null}
          </div>
        )
      },
    },
    {
      id: 'dataRetirada',
      header: 'Retirada',
      meta: { rotulo: 'Retirada', exportar: (c) => formatarData(c.dataRetirada) },
      cell: ({ row }) => (
        <span className="text-texto-suave">{formatarData(row.original.dataRetirada)}</span>
      ),
    },
    {
      id: 'status',
      header: 'Situação',
      meta: { rotulo: 'Situação', exportar: (c) => c.statusDescricao },
      cell: ({ row }) => (
        <Badge variante={CORES_DO_STATUS[row.original.status] ?? 'neutra'}>
          {row.original.statusDescricao}
        </Badge>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Contratos de locação"
        descricao="Cada contrato é uma linha do controle geral: obra, locadora, veículo e condutor. Abra um contrato para ver a linha do tempo e registrar retirada, trocas ou devolução."
      />

      <BarraDeFiltros
        termo={termo}
        aoMudarTermo={(valor) => {
          definirTermo(valor)
          definirPagina(0)
        }}
        placeholder="Buscar por código, obra, placa ou condutor"
      >
        <Select
          value={status}
          onChange={(evento) => {
            definirStatus(evento.target.value as StatusContrato | '')
            definirPagina(0)
          }}
          className="w-52"
          aria-label="Filtrar por situação"
        >
          <option value="">Todas as situações</option>
          {Object.entries(STATUS_DE_CONTRATO).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>
              {rotulo}
            </option>
          ))}
        </Select>
      </BarraDeFiltros>

      <TabelaDeDados
        legenda="Contratos de locação, com veículo e condutor atuais, histórico de trocas e situação"
        colunas={colunas}
        dados={contratos}
        paginacao={{
          pagina,
          tamanho,
          totalElementos: consulta.data?.totalElementos ?? 0,
          totalPaginas: consulta.data?.totalPaginas ?? 0,
        }}
        aoMudarPagina={definirPagina}
        aoMudarTamanho={(novo) => {
          definirTamanho(novo)
          definirPagina(0)
        }}
        ordenacao={ordenacao}
        aoMudarOrdenacao={(nova) => {
          definirOrdenacao(nova)
          definirPagina(0)
        }}
        carregando={consulta.isPending}
        erro={consulta.isError ? consulta.error : undefined}
        aoTentarNovamente={() => {
          void consulta.refetch()
        }}
        aoSelecionarLinha={(contrato) => {
          void navegar(`/contratos/${String(contrato.id)}`)
        }}
        nomeDoArquivo="contratos"
        aoExportar={async () => {
          const todos = exigirSucesso(
            await api.GET('/api/v1/contratos', {
              params: { query: { ...filtros, page: 0, size: TUDO, sort } },
            }),
          )
          return todos.conteudo
        }}
        vazio={
          <EstadoVazio
            icone={<FileSpreadsheet className="size-6" />}
            titulo={filtrando ? 'Nenhum contrato encontrado' : 'Nenhum contrato cadastrado'}
            descricao={
              filtrando
                ? 'Nenhum contrato atende aos filtros aplicados. Ajuste a busca ou limpe a situação selecionada.'
                : 'Os contratos são criados a partir do controle geral de veículos.'
            }
          />
        }
      />
    </div>
  )
}
