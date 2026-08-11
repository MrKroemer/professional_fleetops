import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ColumnDef, SortingState } from '@tanstack/react-table'
import { IdCard, Plus, TriangleAlert } from 'lucide-react'
import { useState } from 'react'

import { FormularioDeCondutor } from './FormularioDeCondutor'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EstadoVazio } from '@/components/ui/estados'
import { Select } from '@/components/ui/select'
import { TabelaDeDados } from '@/components/ui/tabela-de-dados'
import { BarraDeFiltros } from '@/features/cadastros/componentes/BarraDeFiltros'
import { CabecalhoDaPagina } from '@/features/cadastros/componentes/CabecalhoDaPagina'
import { DetalheDoRegistro } from '@/features/cadastros/componentes/DetalheDoRegistro'
import { DialogoDeExclusao } from '@/features/cadastros/componentes/DialogoDeExclusao'
import { STATUS_DE_CONDUTOR, type Condutor, type StatusCondutor } from '@/features/cadastros/tipos'
import { useAutenticacao } from '@/features/auth/use-autenticacao'
import { api, exigirSucesso } from '@/lib/api/client'
import { formatarData, formatarDataHora } from '@/lib/formatters'
import { useDebounce } from '@/lib/use-debounce'

const TUDO = 2000

/** Cadastro de condutores, com acompanhamento da validade da CNH (RN-16). */
export function CondutoresPage() {
  const { temPerfil } = useAutenticacao()
  const podeEditar = temPerfil('ADMIN', 'GESTOR_FROTA')
  const clienteDeConsultas = useQueryClient()

  const [termo, definirTermo] = useState('')
  const [status, definirStatus] = useState<StatusCondutor | ''>('')
  const termoAplicado = useDebounce(termo)

  const [pagina, definirPagina] = useState(0)
  const [tamanho, definirTamanho] = useState(20)
  const [ordenacao, definirOrdenacao] = useState<SortingState>([{ id: 'nome', desc: false }])

  const [criando, definirCriando] = useState(false)
  const [emDetalhe, definirEmDetalhe] = useState<Condutor | null>(null)
  const [emEdicao, definirEmEdicao] = useState<Condutor | null>(null)
  const [emExclusao, definirEmExclusao] = useState<Condutor | null>(null)

  const filtros = {
    ...(termoAplicado ? { termo: termoAplicado } : {}),
    ...(status ? { status } : {}),
  }
  const ordem = ordenacao[0]
  const sort = [`${ordem?.id ?? 'nome'},${ordem?.desc ? 'desc' : 'asc'}`]

  const consulta = useQuery({
    queryKey: ['condutores', { filtros, pagina, tamanho, sort }],
    queryFn: async () =>
      exigirSucesso(
        await api.GET('/api/v1/condutores', { params: { query: { ...filtros, page: pagina, size: tamanho, sort } } }),
      ),
  })

  const exclusao = useMutation({
    mutationFn: async (id: number) =>
      exigirSucesso(await api.DELETE('/api/v1/condutores/{id}', { params: { path: { id } } })),
    onSuccess: async () => {
      definirEmDetalhe(null)
      await clienteDeConsultas.invalidateQueries({ queryKey: ['condutores'] })
    },
  })

  const condutores = consulta.data?.conteudo ?? []
  const filtrando = termoAplicado.length > 0 || status !== ''
  const emAlerta = condutores.filter((condutor) => condutor.cnhEmAlerta).length

  const colunas: ColumnDef<Condutor, unknown>[] = [
    {
      id: 'nome',
      header: 'Condutor',
      enableHiding: false,
      meta: { rotulo: 'Condutor', exportar: (c) => c.nome },
      cell: ({ row }) => (
        <div>
          <p className="font-medium text-texto">{row.original.nome}</p>
          <p className="text-xs text-texto-suave">
            {row.original.cpfFormatado}
            {row.original.cargo ? ` — ${row.original.cargo}` : ''}
          </p>
        </div>
      ),
    },
    {
      id: 'obraAtual',
      header: 'Obra atual',
      enableSorting: false,
      meta: {
        rotulo: 'Obra atual',
        exportar: (c) => (c.obraAtual ? `${c.obraAtual.codigo} — ${c.obraAtual.nome}` : ''),
      },
      cell: ({ row }) => (
        <span className="text-texto-suave">
          {row.original.obraAtual
            ? `${row.original.obraAtual.codigo} — ${row.original.obraAtual.nome}`
            : '—'}
        </span>
      ),
    },
    {
      id: 'cnhValidade',
      header: 'CNH',
      meta: {
        rotulo: 'CNH',
        exportar: (c) => `${c.cnhCategoria ?? ''} ${formatarData(c.cnhValidade)}`.trim(),
      },
      cell: ({ row }) =>
        row.original.cnhValidade ? (
          <div className="text-texto-suave">
            <span className="block">{formatarData(row.original.cnhValidade)}</span>
            <span className="text-xs">{row.original.cnhCategoria ?? '—'}</span>
          </div>
        ) : (
          <span className="text-texto-suave">—</span>
        ),
    },
    {
      id: 'situacaoCnh',
      header: 'Situação da CNH',
      enableSorting: false,
      meta: { rotulo: 'Situação da CNH', exportar: (c) => descricaoDaCnh(c) },
      cell: ({ row }) => <SeloDaCnh condutor={row.original} />,
    },
    {
      id: 'status',
      header: 'Status',
      meta: { rotulo: 'Status', exportar: (c) => c.statusDescricao },
      cell: ({ row }) => (
        <Badge variante={row.original.status === 'ATIVO' ? 'sucesso' : 'neutra'}>
          {row.original.statusDescricao}
        </Badge>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-7xl px-6 py-8">
      <CabecalhoDaPagina
        titulo="Condutores"
        descricao="Funcionários habilitados a conduzir a frota. A validade da CNH é acompanhada aqui: o vencimento bloqueia novo vínculo a contrato."
        acao={
          podeEditar ? (
            <Button onClick={() => { definirCriando(true) }}>
              <Plus aria-hidden="true" />
              Novo condutor
            </Button>
          ) : undefined
        }
      />

      {emAlerta > 0 ? (
        <div
          role="status"
          className="mb-4 flex items-start gap-3 rounded-[var(--radius-base)] border border-atencao/30 bg-atencao-suave/40 px-3 py-2.5"
        >
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-atencao" aria-hidden="true" />
          <p className="text-sm text-texto-suave">
            {emAlerta === 1
              ? '1 condutor desta página está com a CNH vencida ou vencendo nos próximos 60 dias.'
              : `${String(emAlerta)} condutores desta página estão com a CNH vencida ou vencendo nos próximos 60 dias.`}
          </p>
        </div>
      ) : null}

      <BarraDeFiltros
        termo={termo}
        aoMudarTermo={(valor) => { definirTermo(valor); definirPagina(0) }}
        placeholder="Buscar por nome, CPF, cargo ou e-mail"
      >
        <Select
          value={status}
          onChange={(evento) => { definirStatus(evento.target.value as StatusCondutor | ''); definirPagina(0) }}
          className="w-48"
          aria-label="Filtrar por situação"
        >
          <option value="">Todas as situações</option>
          {Object.entries(STATUS_DE_CONDUTOR).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </Select>
      </BarraDeFiltros>

      <TabelaDeDados
        legenda="Condutores cadastrados, com CPF, obra atual, validade e situação da CNH"
        colunas={colunas}
        dados={condutores}
        paginacao={{
          pagina,
          tamanho,
          totalElementos: consulta.data?.totalElementos ?? 0,
          totalPaginas: consulta.data?.totalPaginas ?? 0,
        }}
        aoMudarPagina={definirPagina}
        aoMudarTamanho={(novo) => { definirTamanho(novo); definirPagina(0) }}
        ordenacao={ordenacao}
        aoMudarOrdenacao={(nova) => { definirOrdenacao(nova); definirPagina(0) }}
        carregando={consulta.isPending}
        erro={consulta.isError ? consulta.error : undefined}
        aoTentarNovamente={() => { void consulta.refetch() }}
        aoSelecionarLinha={definirEmDetalhe}
        nomeDoArquivo="condutores"
        aoExportar={async () => {
          const todos = exigirSucesso(
            await api.GET('/api/v1/condutores', { params: { query: { ...filtros, page: 0, size: TUDO, sort } } }),
          )
          return todos.conteudo
        }}
        vazio={
          <EstadoVazio
            icone={<IdCard className="size-6" />}
            titulo={filtrando ? 'Nenhum condutor encontrado' : 'Nenhum condutor cadastrado'}
            descricao={
              filtrando
                ? 'Nenhum registro atende aos filtros aplicados.'
                : 'Cadastre os funcionários que conduzem os veículos da frota.'
            }
            acao={
              podeEditar && !filtrando ? (
                <Button variante="secundaria" tamanho="pequeno" onClick={() => { definirCriando(true) }}>
                  <Plus aria-hidden="true" />
                  Cadastrar condutor
                </Button>
              ) : undefined
            }
          />
        }
      />

      {emDetalhe ? (
        <DetalheDoRegistro
          titulo={emDetalhe.nome}
          subtitulo={`${emDetalhe.cpfFormatado}${emDetalhe.cargo ? ` · ${emDetalhe.cargo}` : ''}`}
          selo={{
            texto: emDetalhe.statusDescricao,
            variante: emDetalhe.status === 'ATIVO' ? 'sucesso' : 'neutra',
          }}
          aviso={
            emDetalhe.cnhVencida ? (
              <div className="flex items-start gap-3 rounded-[var(--radius-base)] border border-critico/30 bg-critico-suave/40 px-3 py-2.5">
                <TriangleAlert className="mt-0.5 size-4 shrink-0 text-critico" aria-hidden="true" />
                <p className="text-sm text-texto-suave">
                  CNH vencida em {formatarData(emDetalhe.cnhValidade)}. Este condutor não pode ser
                  vinculado a um novo contrato até regularizar <span className="whitespace-nowrap">(RN-16)</span>.
                </p>
              </div>
            ) : undefined
          }
          secoes={[
            {
              titulo: 'Identificação',
              campos: [
                { rotulo: 'Nome', valor: emDetalhe.nome },
                { rotulo: 'CPF', valor: emDetalhe.cpfFormatado },
                { rotulo: 'Cargo', valor: emDetalhe.cargo },
                {
                  rotulo: 'Obra atual',
                  valor: emDetalhe.obraAtual
                    ? `${emDetalhe.obraAtual.codigo} — ${emDetalhe.obraAtual.nome}`
                    : null,
                },
                { rotulo: 'Telefone', valor: emDetalhe.telefone },
                { rotulo: 'E-mail', valor: emDetalhe.email },
              ],
            },
            {
              titulo: 'Habilitação',
              campos: [
                { rotulo: 'Número', valor: emDetalhe.cnhNumero },
                { rotulo: 'Categoria', valor: emDetalhe.cnhCategoria },
                { rotulo: 'Validade', valor: formatarData(emDetalhe.cnhValidade) },
                { rotulo: 'Situação', valor: descricaoDaCnh(emDetalhe) },
              ],
            },
            {
              titulo: 'Observações',
              colunas: 1,
              campos: [{ rotulo: 'Anotações', valor: emDetalhe.observacoes, larguraTotal: true }],
            },
            {
              titulo: 'Auditoria',
              campos: [
                { rotulo: 'Cadastrado em', valor: formatarDataHora(emDetalhe.criadoEm) },
                { rotulo: 'Última alteração', valor: formatarDataHora(emDetalhe.atualizadoEm) },
              ],
            },
          ]}
          aoFechar={() => { definirEmDetalhe(null) }}
          podeEditar={podeEditar}
          aoEditar={() => { definirEmEdicao(emDetalhe); definirEmDetalhe(null) }}
          aoExcluir={() => { definirEmExclusao(emDetalhe) }}
        />
      ) : null}

      {criando || emEdicao !== null ? (
        <FormularioDeCondutor
          key={emEdicao?.id ?? 'novo'}
          condutor={emEdicao}
          aoFechar={() => { definirCriando(false); definirEmEdicao(null) }}
        />
      ) : null}

      <DialogoDeExclusao
        aberto={emExclusao !== null}
        aoMudarAbertura={(aberto) => { if (!aberto) { definirEmExclusao(null) } }}
        titulo="Excluir condutor"
        descricao={`O condutor "${emExclusao?.nome ?? ''}" sairá das listagens. O histórico de contratos e lançamentos é preservado.`}
        aoConfirmar={() => exclusao.mutateAsync(emExclusao?.id ?? 0)}
      />
    </div>
  )
}

/** Texto da situação da CNH — usado no selo e na exportação, para não divergirem. */
function descricaoDaCnh(condutor: Condutor): string {
  if (!condutor.cnhValidade) {
    return 'Sem CNH cadastrada'
  }
  if (condutor.cnhVencida) {
    return 'Vencida'
  }
  if (condutor.cnhEmAlerta) {
    return `Vence em ${String(condutor.diasParaVencerCnh ?? 0)} dias`
  }
  return 'Em dia'
}

/** Selo com a situação da CNH (RN-16). */
function SeloDaCnh({ condutor }: { condutor: Condutor }) {
  if (!condutor.cnhValidade) {
    return <Badge variante="neutra">Sem CNH cadastrada</Badge>
  }
  if (condutor.cnhVencida) {
    return <Badge variante="critica">Vencida</Badge>
  }
  if (condutor.cnhEmAlerta) {
    return <Badge variante="atencao">{descricaoDaCnh(condutor)}</Badge>
  }
  return <Badge variante="sucesso">Em dia</Badge>
}
